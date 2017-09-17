package com.reactive.http.server.actor

import java.net.InetSocketAddress
import java.nio.channels.{SelectionKey, ServerSocketChannel}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.dispatch.{RequiresMessageQueue, UnboundedMessageQueueSemantics}
import com.reactive.http.server.actor.SelectionHandler.ChannelAcceptable

import scala.annotation.tailrec

class TcpListner(selectionHandler: ActorRef,
                 channelRegistry: ChannelRegistry,
                 bindCommander: ActorRef,
                 bind: Bind)
  extends Actor with ActorLogging with RequiresMessageQueue[UnboundedMessageQueueSemantics] {

  val serverSocketChannel: ServerSocketChannel = ServerSocketChannel.open
  serverSocketChannel.configureBlocking(false)
  serverSocketChannel.bind(bind.localAddress)
  println(s"Server listening on ${bind.localAddress}")
  channelRegistry.register(serverSocketChannel, SelectionKey.OP_ACCEPT)

  override def receive: Receive = {
    case registration: ChannelRegistration ⇒
      println("Server channel registered. Sending Bound Event")
      bind.handler ! Bound(serverSocketChannel.socket.getLocalSocketAddress.asInstanceOf[InetSocketAddress])
      context.become(bound(registration))
  }

  @tailrec final def acceptAllPending(registration: ChannelRegistration, limit: Int): Unit = {
    println("Accepting all pending connections")
    val socketChannel = serverSocketChannel.accept()
    if (socketChannel != null) {
      println("New connection accepted")
      socketChannel.configureBlocking(false)

      def props(registry: ChannelRegistry) =
        Props(classOf[TcpIncomingConnection], socketChannel, channelRegistry, bind.handler)

      selectionHandler ! SelectionHandlerWorkerCommand(props)

      acceptAllPending(registration, limit - 1)
    }
  }

  def bound(registration: ChannelRegistration): Receive = {
    case ResumeAccepting(batchSize) ⇒ {
      println("Resuming accepting connections")
      registration.enableInterest(SelectionKey.OP_ACCEPT)
    }
    case ChannelAcceptable ⇒ {
      acceptAllPending(registration, 1)
    }
  }
}

