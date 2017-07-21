package com.reactive.http.server.actor

import java.net.InetSocketAddress
import java.nio.channels.{SelectionKey, SocketChannel}

import akka.actor.{Actor, ActorRef, Props}
import akka.util.ByteString
import com.reactive.http.parser.HttpRequestParser
import com.reactive.http.server.actor.TcpManager._


object TcpManager {

  sealed trait Message

  trait Command extends Message

  trait Event extends Message

  final case class Bind(handler: ActorRef, localAddress: InetSocketAddress) extends Command

  final case class Bound(address: InetSocketAddress) extends Event

  final case class ResumeAccepting(batchSize: Int) extends Command

  final case class RegisterIncomingConnection(socketChannel: SocketChannel, props: (ChannelRegistry) ⇒ Props) extends Command with Event

  final case class Register(handler: ActorRef, keepOpenOnPeerClosed: Boolean = false, useResumeWriting: Boolean = true) extends Command

  final case class Connected(remoteAddress: InetSocketAddress, localAddress: InetSocketAddress) extends Event

  case object ResumeReading extends Command

  final case class Received(data: ByteString) extends Event

  case object SuspendReading extends Command


  sealed abstract class WriteCommand extends Command

  final case class Write(data: ByteString, ack: Event) extends WriteCommand


  case class NoAck(token: Any) extends Event

  /**
    * Default [[NoAck]] instance which is used when no acknowledgment information is
    * explicitly provided. Its “token” is `null`.
    */
  object NoAck extends NoAck(null)

  object Write {
    val empty: Write = Write(ByteString.empty, NoAck)

    def apply(data: ByteString): Write =
      if (data.isEmpty) empty else Write(data, NoAck)
  }

  case object CloseCommand extends Command

  case object ConnectionClosed extends Event

}

//this is just to make it map easily to akka.io.
abstract class SelectorBasedManager() extends Actor {
  val selectorPool = context.actorOf(Props(new SelectionHandler))

  def selector = selectorPool
}

class TcpManager extends SelectorBasedManager {

  import TcpManager._

  override def receive: Receive = {
    case b@Bind(handler, localAddress) ⇒ {
      println("Binding")
      val commander = sender()
      selector ! Bind(commander, localAddress)
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
      val httpRequest = new HttpRequestParser().parseMessage(data) //TODO: make httprequestparser stateful
      println(s"Read http request $httpRequest")
      connection ! Write(ByteString(s"Hello World Of NIO! Handling ${httpRequest}")) //this will be written by HttpResponse in bidi flow
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
