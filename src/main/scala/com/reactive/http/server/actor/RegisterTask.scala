package com.reactive.http.server.actor

import java.nio.channels.spi.AbstractSelector
import java.nio.channels.{SelectableChannel, SelectionKey}

import akka.actor.ActorRef

import scala.concurrent.ExecutionContext

class RegisterTask(context: ExecutionContext, selector: AbstractSelector, channel: SelectableChannel, initialOps: Int, channelActor: ActorRef) extends Runnable {

  override def run(): Unit = {
    val key = channel.register(selector, initialOps, channelActor)
    channelActor ! new ChannelRegistration {

      def enableInterest(ops: Int): Unit = enableInterestOps(key, ops)

      def disableInterest(ops: Int): Unit = disableInterestOps(key, ops)

      def cancelKey(key: SelectionKey): Unit = ???

      def cancel(): Unit = {
        // On Windows the selector does not effectively cancel the registration until after the
        // selector has woken up. Because here the registration is explicitly cancelled, the selector
        // will be woken up which makes sure the cancellation (e.g. sending a RST packet for a cancelled TCP connection)
        // is performed immediately.
        cancelKey(key)
      }


      // always set the interest keys on the selector thread,
      // benchmarks show that not doing so results in lock contention
      private def enableInterestOps(key: SelectionKey, ops: Int): Unit = {
        context.execute(() ⇒
          {
              println(s"Setting ${ops} on ${key.channel()}")
              val currentOps = key.interestOps
              val newOps = currentOps | ops
              if (newOps != currentOps) key.interestOps(newOps)
          }
        )
        selector.wakeup() //because its the same context, we need to
      }
    }
    def disableInterestOps(key: SelectionKey, ops: Int): Unit = ???
  }
}
