package com.reactive.http.server.stream

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import akka.stream._
import akka.stream.scaladsl.{BidiFlow, Flow, Keep, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.reactive.http.model.{HttpRequest, HttpResponse}
import com.reactive.http.server.actor.TcpManager

import scala.concurrent.Future


object Server {

  def parsing(): Flow[ByteString, HttpRequest, NotUsed] = {
    val rootParser = new HttpRequestParser()
    val requestParsingFlow: Flow[ByteString, HttpRequest, NotUsed] = Flow[ByteString].via(rootParser)
    requestParsingFlow
  }

  def rendering(): Flow[HttpResponse, ByteString, NotUsed] = Flow[HttpResponse].via(HttpResponseRenderer)

  def bindAndHandle(handler: HttpRequest ⇒ HttpResponse, endpoint: InetSocketAddress)(implicit materializer: Materializer): Unit = {

    val handlerFlow: Flow[HttpRequest, HttpResponse, Future[Done]] = Flow[HttpRequest].map(handler).watchTermination()(Keep.right)

    val parsingRendering: BidiFlow[HttpResponse, ByteString, ByteString, HttpRequest, NotUsed] = BidiFlow.fromFlows(rendering(), parsing())

    /**
      *
      * +--------------------------------------+
      * | Resulting Flow                       |
      * |                                      |
      * | +------+                   +------+  |
      * | |      | ~HttpResponse~>   |      | ~~> ByteString
      * | | flow |                   | bidi |  |
      * | |      | <~HttpRequest~    |      | <~~ ByteString
      * | +------+                   +------+  |
      * +--------------------------------------+
      *
      */
    val finalFlow: Flow[ByteString, ByteString, Future[Done]] = handlerFlow.join(parsingRendering)

    val system = ActorSystem("Server")
    val tcpManager = system.actorOf(Props(new TcpManager), "tcpManager")

    val source: Source[TcpStream.IncomingConnection, Future[TcpStream.ServerBinding]] = Source.fromGraph(new TcpHandlingGraphStage(tcpManager, endpoint))

    val mapedAsyncSource: Source[Done, Future[TcpStream.ServerBinding]] = source.mapAsyncUnordered(10)(
      incomingConnection ⇒ finalFlow.join(incomingConnection.flow).run
    )

    mapedAsyncSource.runWith(Sink.ignore)
  }

  /*def rendering(): Flow[ByteString, ByteString, NotUsed] = {
    val renderer = HttpResponseRendererStale
    val renderingFlow = Flow[ByteString].via(renderer)
    renderingFlow
  }

  def bindAndHandle(handler: HttpRequest ⇒ ByteString, endpoint: InetSocketAddress)(implicit materializer:Materializer): Unit = {

    val handlerFlow: Flow[HttpRequest, ByteString, NotUsed] = Flow[HttpRequest].map(handler)

    val parsingRendering: BidiFlow[ByteString, ByteString, ByteString, HttpRequest, NotUsed] = BidiFlow.fromFlows(rendering(), parsing())

    val resultFlow: Flow[ByteString, ByteString, Future[Done]] = fuzeServerFlow(handlerFlow, parsingRendering)

    val system = ActorSystem("Server")
    val tcpManager = system.actorOf(Props(new TcpManager), "tcpManager") //TODO: this should be moved to actorsystem extension
    val source: Source[TcpStream.IncomingConnection, Future[TcpStream.ServerBinding]] = Source.fromGraph(new TcpHandlingGraphStage(tcpManager, new InetSocketAddress("localhost", 5555)))
    source.mapAsyncUnordered(10) { incoming ⇒
      println(s"Running ${incoming}")
      try {
        resultFlow.joinMat(incoming.flow)(Keep.left)
          .run().recover {
          case NonFatal(ex) ⇒ Done
        }(ExecutionContext.global)
      } catch {
          case e⇒ {
            println(s"${e}")
            throw e
          }
      }
    }.to(Sink.ignore).run()
  }

  def fuzeServerFlow(handlerFlow: Flow[HttpRequest, ByteString, NotUsed], parsingRendering: BidiFlow[ByteString, ByteString, ByteString, HttpRequest, NotUsed]) = {
    val flowFromHandler: Flow[HttpRequest, ByteString, Future[Done]] =
      Flow[HttpRequest]
        .watchTermination()(Keep.right)
        .viaMat(handlerFlow)(Keep.left)

    val resultFlow: Flow[ByteString, ByteString, Future[Done]] = flowFromHandler.joinMat(parsingRendering)(Keep.left)
    resultFlow
  }*/
}
