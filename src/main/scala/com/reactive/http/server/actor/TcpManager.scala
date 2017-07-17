package com.reactive.http.server.actor

import java.net.InetSocketAddress
import java.nio.channels.SelectableChannel
import java.nio.channels.spi.{AbstractSelector, SelectorProvider}

import akka.actor.{Actor, ActorRef, NoSerializationVerificationNeeded, Props}
import com.typesafe.config.Config

import scala.concurrent.ExecutionContext

sealed trait TcpMessages

object TcpManager {

  case class Bind(handler: ActorRef, localAddress: InetSocketAddress) extends TcpMessages

}

class TcpManager extends SelectorBasedManager {

  println("started")

  import TcpManager._

  override def receive: Receive = {
    case Bind(handler, localAddress) ⇒ {
    }
  }
}


class SelectionHandlerSettings(config: Config) {
  //  system.settings.config.getConfig("akka.io.tcp")
}

class SelectionHandler extends Actor {
  private[this] val registry = {
    val SelectorDispatcher: String = context.system.settings.config.getConfig("com-reactive-tcp").getString("selector-dispatcher")
    val dispatcher = context.system.dispatchers.lookup(SelectorDispatcher)
    new ChannelRegistry(context.dispatcher)
  }

  // It uses SerializedSuspendableExecutionContext with PinnedDispatcher (This dispatcher dedicates a unique thread for each
  // actor using it; i.e. each actor will have its own thread pool with only one thread in the pool)
  class ChannelRegistry(executionContext: ExecutionContext) {

    private[this] val selector: AbstractSelector = SelectorProvider.provider.openSelector

    private[this] val select = new SelectTask(executionContext, selector)
    executionContext.execute(select) // start selection "loop"


    def register(channel: SelectableChannel, initialOps: Int)(implicit channelActor: ActorRef): Unit = {
      val register = new RegisterTask(executionContext, selector, channel, initialOps, channelActor)
    }

    private def execute(task: Runnable): Unit = {
      executionContext.execute(task)
    }
  }


  override def receive: Receive = {
    case _ ⇒ println("in SelectionHandler")
  }
}

trait ChannelRegistration extends NoSerializationVerificationNeeded {
  def enableInterest(op: Int): Unit

  def disableInterest(op: Int): Unit

  /**
    * Explicitly cancel the registration
    *
    * This wakes up the selector to make sure the cancellation takes effect immediately.
    */
  def cancel(): Unit
}

abstract class SelectorBasedManager() extends Actor {
  private val selectorPool = context.actorOf(Props(new SelectionHandler))
}

