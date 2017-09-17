package com.reactive.http.server.actor

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, Props}
import akka.util.ByteString
import com.reactive.http.parser.HttpRequestParser


//this is just to make it map easily to akka.io.
abstract class SelectorBasedManager() extends Actor {
  val selectorPool = context.actorOf(Props(new SelectionHandler))

  def selector = selectorPool
}

class TcpManager extends SelectorBasedManager {

  override def receive: Receive = {
    case b:Bind ⇒ {
      println("Binding")

      val commander = sender()

      def props(registry: ChannelRegistry) =
        Props(classOf[TcpListner], selector, registry, commander, b)

      selectorPool ! SelectionHandlerWorkerCommand(props)
    }
  }
}


//this represents a TcpStreamLogic graph stage. Handles reading from and writing to connection based on pull/push
class TcpConnectionHandler(connection: ActorRef, remoteAddress: InetSocketAddress) extends Actor {
  override def preStart(): Unit = {
    connection ! Register(self, keepOpenOnPeerClosed = true, useResumeWriting = false)
  }

  override def receive: Receive = {
    case Received(data) ⇒
      val httpRequest = new HttpRequestParser().parseMessage(data, 0) //TODO: make httprequestparser stateful
      println(s"Read http request $httpRequest")
      connection ! Write(ByteString(s"HTTP/1.1 200 OK \r\n")) //this will be written by HttpResponse in bidi flow
      self ! "close"

    case "close" =>
      connection ! CloseCommand
  }
}


//Equivalent of ConnectionSourceStage
class ServerActor(val endpoint: InetSocketAddress) extends Actor {
  val tcpManager = context.actorOf(Props(new TcpManager), "tcpManager") //TODO: this should be moved to actorsystem extension
  var listener: ActorRef = _
  var numberOfConnections = 0

  override def preStart(): Unit = {
    tcpManager ! Bind(self, endpoint) //self sent to
  }

  override def receive: Receive = {
    case Bound(address) ⇒
      println("Bound")
      listener = sender()
      println(s"listner set to ${listener}")

    case Connected(remoteAddress, localAddress) ⇒
      val connection = sender()
      numberOfConnections += 1
      context.actorOf(Props(new TcpConnectionHandler(connection, remoteAddress)), s"tcpConnectionhander${numberOfConnections}")
      println(s"listner is ${listener}")
      listener ! ResumeAccepting(1)
    case a@_ ⇒ println(s"_______________Unhandled message_______________${a}")
  }
}
