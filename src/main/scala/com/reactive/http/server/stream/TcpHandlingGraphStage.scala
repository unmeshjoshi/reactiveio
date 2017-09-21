package com.reactive.http.server.stream

import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorRef
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.stream.stage._
import akka.util.ByteString
import com.reactive.http.server.actor._
import com.reactive.http.server.stream.TcpStream.IncomingConnection

import scala.concurrent.{Future, Promise}

class TcpHandlingGraphStage(
                             val tcpManager: ActorRef,
                             val endpoint: InetSocketAddress)
  extends GraphStageWithMaterializedValue[SourceShape[TcpStream.IncomingConnection], Future[TcpStream.ServerBinding]] {

  val out: Outlet[TcpStream.IncomingConnection] = Outlet("IncomingConnections.out")
  val shape: SourceShape[TcpStream.IncomingConnection] = SourceShape(out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[TcpStream.ServerBinding]) = {
    val bindingPromise = Promise[TcpStream.ServerBinding]

    val logic = new TimerGraphStageLogic(shape) {
      implicit def self: ActorRef = stageActor.ref

      var listener: ActorRef = _
      var numberOfConnections = 0

      override def preStart(): Unit = {
        getStageActor(receive)
        tcpManager ! Bind(self, endpoint)
      }


      def incomingConnectionFor(connection: ActorRef, c: Connected): IncomingConnection = {
        IncomingConnection(c.localAddress, c.remoteAddress, Flow.fromGraph(new IncomingConnectionStage(connection, c.remoteAddress, false)))
      }

      private def receive(evt: (ActorRef, Any)): Unit = {
        val sender = evt._1
        val msg = evt._2
        msg match {
          case Bound(address) ⇒
            println("Bound")
            listener = sender
            println(s"listner set to ${listener}")
            if (isAvailable(out)) listener ! ResumeAccepting

          case c: Connected ⇒
            val connection = sender
            numberOfConnections += 1
            push(out, incomingConnectionFor(connection, c)) //TODO: Implementing handling coonnection
            //            context.actorOf(Props(new TcpConnectionHandler(connection, remoteAddress)), s"tcpConnectionhander${numberOfConnections}")
            println(s"listner is ${listener}")
            listener ! ResumeAccepting(1)
          case a@_ ⇒ println(s"_______________Unhandled message_______________${a}")
        }
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          println("ObPull, resuming accepting connections")
          // Ignore if still binding
          if (listener ne null) listener ! ResumeAccepting(1)
        }
      })
    }
    (logic, bindingPromise.future)
  }
}

class TcpStreamLogic(val shape: FlowShape[ByteString, ByteString], connection: ActorRef, remoteAddress: InetSocketAddress) extends GraphStageLogic(shape) {
  implicit def self: ActorRef = stageActor.ref

  private def bytesIn = shape.in

  private def bytesOut = shape.out

  val readHandler = new OutHandler {
    override def onPull(): Unit = {
      println("Resuming reading from connection")
      connection ! ResumeReading
    }
  }

  val writeHandler = new InHandler {
    override def onPush(): Unit = {
      val elem = grab(bytesIn)
      println(s"Writing ${elem}")
      connection ! Write(elem.asInstanceOf[ByteString], WriteAck)
    }

    override def onUpstreamFinish(): Unit = {
      println("Closing connection now")
      connection ! CloseCommand
    }
  }

  // No reading until role have been decided
  setHandler(bytesOut, readHandler)
  setHandler(bytesIn, writeHandler)

  override def preStart(): Unit = {
    println("TCPStreamLogic prestart")
    val actor: GraphStageLogic.StageActor = getStageActor(connected)
    actor.watch(connection)
    connection ! Register(actor.ref, keepOpenOnPeerClosed = true, useResumeWriting = false)
    pull(bytesIn) //this pull triggers start reading data from connection and start writing to connection
  }


  private def connected(evt: (ActorRef, Any)): Unit = {
    val sender = evt._1
    val msg = evt._2
    msg match {
      case Received(data)⇒
        push(bytesOut, data)
      case WriteAck ⇒ if (!isClosed(bytesIn)) pull(bytesIn)
      case c: Connected ⇒
        setHandler(bytesOut, readHandler)
        stageActor.watch(connection)
        if (isAvailable(bytesOut)) connection ! ResumeReading
        pull(bytesIn)

    }
  }
}

private class IncomingConnectionStage(connection: ActorRef, remoteAddress: InetSocketAddress, halfClose: Boolean)
  extends GraphStage[FlowShape[ByteString, ByteString]] {

  private val hasBeenCreated = new AtomicBoolean(false)

  val bytesIn: Inlet[ByteString] = Inlet("IncomingTCP.in")
  val bytesOut: Outlet[ByteString] = Outlet("IncomingTCP.out")

  override def initialAttributes = Attributes.name("IncomingConnection")

  val shape: FlowShape[ByteString, ByteString] = FlowShape(bytesIn, bytesOut)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    if (hasBeenCreated.get) throw new IllegalStateException("Cannot materialize an incoming connection Flow twice.")
    hasBeenCreated.set(true)

    println("Creating TcpStreamLogic")
    new TcpStreamLogic(shape, connection, remoteAddress)
  }

  override def toString = s"TCP-from($remoteAddress)"
}
