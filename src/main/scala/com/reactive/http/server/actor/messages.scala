package com.reactive.http.server.actor

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.util.ByteString

sealed trait Message

trait Command extends Message

trait Event extends Message

final case class Bind(handler: ActorRef, localAddress: InetSocketAddress) extends Command

final case class Bound(address: InetSocketAddress) extends Event

final case class ResumeAccepting(batchSize: Int) extends Command

final case class SelectionHandlerWorkerCommand(props: (ChannelRegistry) ⇒ Props) extends Command with Event

final case class Register(handler: ActorRef, keepOpenOnPeerClosed: Boolean = false, useResumeWriting: Boolean = true) extends Command

final case class Connected(remoteAddress: InetSocketAddress, localAddress: InetSocketAddress) extends Event

case object ResumeReading extends Command

final case class Received(data: ByteString) extends Event

case object SuspendReading extends Command

sealed abstract class WriteCommand extends Command

case class CloseCommand() extends Command

case object ConnectionClosed extends Event

final case class Write(data: ByteString, ack: Event) extends WriteCommand


case class NoAck(token: Any) extends Event
case object WriteAck extends Event

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