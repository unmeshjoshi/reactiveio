package com.reactive.http.server

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp.{Connect, Connected}
import akka.io.{IO, Tcp}
import com.reactive.http.server.actor.TcpManager

object ActorBasedNIOServer {
  def props(remote: InetSocketAddress, replies: ActorRef) = Props(new ActorBasedNIOServer(remote, replies))
}

class ActorBasedNIOServer(remote: InetSocketAddress, listener: ActorRef) extends Actor {

  implicit var actorSystem  = ActorSystem("system")
  private val manager: ActorRef = IO(Tcp)

  manager ! Connect(remote)

  override def receive: Receive = {
    case Connected(`remote`, local) ⇒
      println("hello")
      println(s"$remote -- $local")
    case _ ⇒ println(" in connecting")
  }
}

object Server {
  def props(localAddress: InetSocketAddress): Props = Props(new Server(localAddress))
}
class Server(localAddress: InetSocketAddress) extends Actor {

  private val tcpManager: ActorRef = context.system.actorOf(Props(new TcpManager()))

  println(tcpManager.toString())
  tcpManager ! TcpManager.Bind(self, localAddress)

  override def receive: Receive = {
    case _ ⇒ println("in server")
  }
}

object Main extends App {

  private val actorSystem = ActorSystem("system")

  private val localAddress = new InetSocketAddress("localhost", 5555)
  private val connector = ActorBasedNIOServer.props(localAddress, ActorRef.noSender)

  actorSystem.actorOf(Server.props(localAddress))
  actorSystem.actorOf(connector)
}
