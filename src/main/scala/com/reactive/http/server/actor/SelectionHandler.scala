package com.reactive.http.server.actor

import java.nio.channels.SelectableChannel
import java.nio.channels.spi.{AbstractSelector, SelectorProvider}

import akka.actor.{Actor, ActorRef, NoSerializationVerificationNeeded, Props}
import com.reactive.http.server.actor.TcpManager.{Bind, RegisterIncomingConnection}

import scala.concurrent.ExecutionContext

/**
  * Interface behind which we hide our selector management logic from the connection actors
  */
trait ChannelRegistry {
  /**
    * Registers the given channel with the selector, creates a ChannelRegistration instance for it
    * and dispatches it back to the channelActor calling this `register`
    */
  def register(channel: SelectableChannel, initialOps: Int)(implicit channelActor: ActorRef)
}


object SelectionHandler {

  sealed trait SelectorEvents

  case object ChannelConnectable extends SelectorEvents

  case object ChannelAcceptable extends SelectorEvents

  case object ChannelReadable extends SelectorEvents

  case object ChannelWritable extends SelectorEvents

}

class SelectionHandler extends Actor {
  private[this] val registry = {
    val SelectorDispatcher: String = context.system.settings.config.getConfig("com-reactive-tcp").getString("selector-dispatcher")
    val dispatcher = context.system.dispatchers.lookup(SelectorDispatcher)
    new ChannelRegistryImpl(dispatcher)
  }

  // It uses SerializedSuspendableExecutionContext with PinnedDispatcher (This dispatcher dedicates a unique thread for each
  // actor using it; i.e. each actor will have its own thread pool with only one thread in the pool)
  class ChannelRegistryImpl(executionContext: ExecutionContext) extends ChannelRegistry {
    println("created channel registry")

    private[this] val selector: AbstractSelector = SelectorProvider.provider.openSelector

    private[this] val select = new SelectTask(executionContext, selector)
    println("scheduling selector task")
    executionContext.execute(select) // start selection "loop"


    def register(channel: SelectableChannel, initialOps: Int)(implicit channelActor: ActorRef): Unit = {
      val register = new RegisterTask(executionContext, selector, channel, initialOps, channelActor)
      execute(register)
      println("Waking up selector")
      selector.wakeup()
    }


    private def execute(task: Runnable): Unit = {
      executionContext.execute(task)
    }
  }

  override def receive: Receive = {
    case b: Bind ⇒
      println("SelectionHandler bind Creating TcpListner")
      val bindCommander = sender()
      context.actorOf(Props(new TcpListner(self, registry, bindCommander, b)))
    //in case of akka http these are sent as worker commands ⇒
    //create tcplistner
    //      println("in SelectionHandler")
    case RegisterIncomingConnection(channel, props) ⇒ context.actorOf(props(registry)) //creation of incoming connection
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

