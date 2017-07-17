package com.reactive.http.server.actor

import java.nio.channels.SelectionKey.{OP_READ, OP_WRITE}
import java.nio.channels.spi.AbstractSelector
import java.nio.channels.{CancelledKeyException, SelectableChannel, SelectionKey, Selector}

import akka.actor.{Actor, ActorRef, Props}

import scala.concurrent.ExecutionContext

class RegisterTask(context: ExecutionContext, selector: AbstractSelector, channel: SelectableChannel, initialOps: Int, channelActor: ActorRef) extends Runnable {

  override def run(): Unit = {
    val key = channel.register(selector, initialOps, channelActor)
    channelActor ! new ChannelRegistration {

      def enableInterestOps(key: SelectionKey, ops: Int): Unit = ???

      def enableInterest(ops: Int): Unit = enableInterestOps(key, ops)

      def disableInterestOps(key: SelectionKey, ops: Int): Unit = ???

      def disableInterest(ops: Int): Unit = disableInterestOps(key, ops)

      def cancelKey(key: SelectionKey): Unit = ???

      def cancel(): Unit = {
        // On Windows the selector does not effectively cancel the registration until after the
        // selector has woken up. Because here the registration is explicitly cancelled, the selector
        // will be woken up which makes sure the cancellation (e.g. sending a RST packet for a cancelled TCP connection)
        // is performed immediately.
        cancelKey(key)
      }
    }
  }
}
