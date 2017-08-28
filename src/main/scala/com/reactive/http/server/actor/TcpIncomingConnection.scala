package com.reactive.http.server.actor

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey.OP_READ
import java.nio.channels.SocketChannel

import akka.actor.{Actor, ActorRef}
import akka.util.ByteString
import com.reactive.http.server.actor.SelectionHandler.{ChannelReadable, ChannelWritable}
import com.reactive.http.server.actor.TcpManager._
import com.reactive.http.server.actor.Writer.EmptyPendingWrite

import scala.annotation.tailrec
import scala.concurrent.duration.Duration

class TcpIncomingConnection(channel: SocketChannel,
                            registry: ChannelRegistry,
                            bindHandler: ActorRef) extends Actor {
  var registration: Option[ChannelRegistration] = _


  registry.register(channel, initialOps = 0)


  def receive = {
    case registration: ChannelRegistration ⇒ completeConnect(registration, bindHandler)
  }

  def suspendReading(registration: ChannelRegistration, handler: ActorRef) = ???

  def handleClose(registration: ChannelRegistration, handler: ActorRef, someRef: Some[ActorRef]) = {
    println("Closing channel.")
    channel.close()
  }


  def doWrite(handler: ActorRef): Unit = {
    pendingWrite.doWrite()
  }

  var pendingWrite:Writer.PendingWrite = Writer.EmptyPendingWrite

  def handleWriteMessages(registration: ChannelRegistration, handler: ActorRef): Receive = {
    case ChannelWritable ⇒
      println("Channel is writable")
      doWrite(handler)
    case write: WriteCommand ⇒
      println(s"Accepting ${write}")
      pendingWrite = Writer.PendingWrite(sender(), write, channel, Some(registration))
      if (writePending) doWrite(handler)
    case close:CloseCommand ⇒ channel.close()
  }

  def writePending = pendingWrite ne EmptyPendingWrite

  def connected(registration: ChannelRegistration, handler: ActorRef): Receive =
    handleWriteMessages(registration, handler) orElse {
      case SuspendReading ⇒ suspendReading(registration, handler)
      case ResumeReading ⇒ resumeReading(registration)
      case ChannelReadable ⇒ doRead(registration, handler)
      case CloseCommand ⇒ handleClose(registration, handler, Some(sender()))
    }


  trait ReadResult

  object AllRead extends ReadResult

  object EndOfStream extends ReadResult

  object MoreDataWaiting extends ReadResult


  def doRead(registration: ChannelRegistration, handler: ActorRef) = {
    println("Reading data from connection")

    @tailrec def innerRead(buffer: ByteBuffer, remainingLimit: Int): ReadResult =

      if (remainingLimit > 0) {
        // never read more than the configured limit
        buffer.clear()
        val maxBufferSpace = math.min(128000, remainingLimit) //128kb
        //        buffer.limit(maxBufferSpace)
        val readBytes = channel.read(buffer)
        buffer.flip()
        if (readBytes > 0) handler ! Received(ByteString(buffer))

        readBytes match {
          case `maxBufferSpace` ⇒ innerRead(buffer, remainingLimit - maxBufferSpace)
          case x if x >= 0 ⇒ AllRead
          case -1 ⇒ EndOfStream
          case _ ⇒
            throw new IllegalStateException("Unexpected value returned from read: " + readBytes)
        }
      } else MoreDataWaiting

    val buffer = ByteBuffer.allocate(2048)
    try innerRead(buffer, Int.MaxValue /*unlimited*/) match {
      case AllRead ⇒
        registration.enableInterest(OP_READ)
      case MoreDataWaiting ⇒
        self ! ChannelReadable
      case EndOfStream if channel.socket.isOutputShutdown ⇒
      //        if (TraceLogging) log.debug("Read returned end-of-stream, our side already closed")
      //        doCloseConnection(info.handler, closeCommander, ConfirmedClosed)
      case EndOfStream ⇒
      //        if (TraceLogging) log.debug("Read returned end-of-stream, our side not yet closed")
      //        handleClose(info, closeCommander, PeerClosed)
    } catch {
      case e: IOException ⇒ throw new RuntimeException(e)
    }
  }

  /** connection established, waiting for registration from user handler */
  def waitingForRegistration(registration: ChannelRegistration, commander: ActorRef): Receive = {
    case Register(handler, keepOpenOnPeerClosed, useResumeWriting) ⇒
      println("Client steamlogic sent registration")
      resumeReading(registration)
      doRead(registration, handler) // immediately try reading, pullMode is handled by readingSuspended
      context.setReceiveTimeout(Duration.Undefined)
      context.become(connected(registration, handler))
  }


  /** used in subclasses to start the common machinery above once a channel is connected */
  def completeConnect(registration: ChannelRegistration, commander: ActorRef): Unit = {
    println("Input Connection Registered")

    this.registration = Some(registration)
    commander ! Connected(
      channel.socket.getRemoteSocketAddress.asInstanceOf[InetSocketAddress],
      channel.socket.getLocalSocketAddress.asInstanceOf[InetSocketAddress])

    context.become(waitingForRegistration(registration, commander))
  }

  def resumeReading(registration: ChannelRegistration): Unit = {
    registration.enableInterest(OP_READ)
  }
}
