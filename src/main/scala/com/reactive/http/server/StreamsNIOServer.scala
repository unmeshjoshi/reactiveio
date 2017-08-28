package com.reactive.http.server

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.reactive.http.model.HttpRequest
import com.reactive.http.server.stream.Server

object StreamBasedNIOServer extends App {
  implicit val system = ActorSystem("StreamServer")
  implicit val materializer = ActorMaterializer()
  Server.bindAndHandle( (request:HttpRequest) ⇒ ByteString(s"Hello from ${request.target}"), new InetSocketAddress("localhost", 5555)/*Figure out how to send request handling flow*/)
}
