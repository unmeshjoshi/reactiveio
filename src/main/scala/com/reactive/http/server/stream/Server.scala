package com.reactive.http.server.stream

import java.net.InetSocketAddress

import akka.NotUsed
import akka.actor.{ActorSystem, Props}
import akka.stream._
import akka.stream.scaladsl.{BidiFlow, Flow, Keep, RunnableGraph, Sink, Source}
import akka.util.ByteString
import com.reactive.http.model.HttpRequest
import com.reactive.http.server.actor.TcpManager

import scala.concurrent.Future


object Server {

  def parsing(): Flow[ByteString, HttpRequest, NotUsed] = {
    val rootParser = new HttpRequestParser()
    val requestParsingFlow: Flow[ByteString, HttpRequest, NotUsed] = Flow[ByteString].via(rootParser)
    requestParsingFlow
  }

  def rendering(): Flow[Any, ByteString, NotUsed] = {
    val renderer = HttpResponseRenderer
    val renderingFlow = Flow[Any].via(renderer)
    renderingFlow
  }

  def bindAndHandle(endpoint: InetSocketAddress)(implicit materializer: Materializer) = {
    val parsingRendering = BidiFlow.fromFlows(rendering(), parsing())
    println(parsingRendering.traversalBuilder)

    val system = ActorSystem("Server")
    val tcpManager = system.actorOf(Props(new TcpManager), "tcpManager") //TODO: this should be moved to actorsystem extension
    val source: Source[TcpStream.IncomingConnection, Future[TcpStream.ServerBinding]] = Source.fromGraph(new TcpHandlingGraphStage(tcpManager, new InetSocketAddress("localhost", 5555)))
    source.mapAsyncUnordered(10) { incoming ⇒
      println(s"Running ${incoming}")

      val joinedFlow: Flow[Any, HttpRequest, NotUsed] = parsingRendering.joinMat(incoming.flow)(Keep.left)
      val sinkJoined = joinedFlow.to(Sink.ignore)
      RunnableGraph(sinkJoined.traversalBuilder).run()

    }.to(Sink.ignore).run()
  }
}
