package com.reactive.http.server

import java.net.InetSocketAddress

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.reactive.http.server.stream.Server

import scala.concurrent.Future

object StreamBasedNIOServer extends App {
  implicit val system = ActorSystem("StreamServer")
  implicit val materializer = ActorMaterializer()
  val flow: Flow[ByteString, ByteString, Future[Done]] = _
  Server.bindAndHandle(new InetSocketAddress("localhost", 5555), flow, materializer)
}
