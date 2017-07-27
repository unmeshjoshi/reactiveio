package com.reactive.http.server.stream

import java.net.InetSocketAddress

import akka.{Done, NotUsed}
import akka.actor.{ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Source}
import akka.util.ByteString
import com.reactive.http.server.actor.TcpManager

import scala.concurrent.Future

object Server {

  def bindAndHandle(endpoint: InetSocketAddress, fullLayer:Flow[ByteString, ByteString, Future[Done]], materializer: Materializer) = {
    val system = ActorSystem("Server")
    val tcpManager = system.actorOf(Props(new TcpManager), "tcpManager") //TODO: this should be moved to actorsystem extension
    val source = Source.fromGraph(new TcpHandlingGraphStage(tcpManager, new InetSocketAddress("localhost", 5555)))
    source.mapAsyncUnordered(10) {
      (incoming: TcpStream.IncomingConnection) ⇒ {
        fullLayer
          .joinMat(incoming.flow)(Keep.left)
          .run()
      }
    }
  }
}
