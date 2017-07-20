package com.reactive.http.server.actor

import java.net.InetSocketAddress
import java.nio.channels.SocketChannel

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp.{Connected, Received, Register, ResumeAccepting}
import com.reactive.http.parser.HttpRequestParser
import com.reactive.http.server.actor.TcpManager.{Bind, Bound}


object TcpManager {
  trait Command

  final case class Bind(handler: ActorRef, localAddress: InetSocketAddress) extends Command

  final case class Bound(address: InetSocketAddress) extends Command

  final case class ResumeAccepting(batchSize: Int) extends Command

  final case class RegisterIncomingConnection(socketChannel: SocketChannel, props: (ChannelRegistry) ⇒ Props)

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


class ClientActor(val endpoint:InetSocketAddress) extends Actor {
  val tcpManager = context.actorOf(Props(new TcpManager)) //TODO: this should be moved to actorsystem extension
  var listener: ActorRef = _

  override def preStart(): Unit = {
    tcpManager ! Bind(self, endpoint) //self sent to
  }

  override def receive: Receive = {
    case Bound ⇒
      println("Bound")
      listener = sender()
      listener ! ResumeAccepting
    case Connected(remoteAddress, localAddress) ⇒
      val connection = sender()
      connection ! Register(self, keepOpenOnPeerClosed = true, useResumeWriting = false)
    case Received(data) ⇒
      val httpRequest = new HttpRequestParser().parseMessage(data) //TODO: make httprequestparser stateful
      println(s"Read http request $httpRequest")
  }
}


object Server extends App {
  val system = ActorSystem("Server")
  system.actorOf(Props(new ClientActor(new InetSocketAddress("localhost", 5555))))
}

