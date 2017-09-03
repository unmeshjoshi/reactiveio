package com.reactive.http.server

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.reactive.http.model.{HttpRequest, HttpResponse}
import com.reactive.http.server.stream.Server

object StreamBasedNIOServer extends App {
  implicit val system: ActorSystem = ActorSystem("StreamServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  Server.bindAndHandle((request:HttpRequest) ⇒ HttpResponse(s"Hello from ${request.target}"), new InetSocketAddress("localhost", 5555)/*Figure out how to send request handling flow*/)
}
