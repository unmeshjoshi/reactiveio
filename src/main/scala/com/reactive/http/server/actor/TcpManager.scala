package com.reactive.http.server.actor

import akka.actor.{Actor, Props}


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
