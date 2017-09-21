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
    val rootParser = new HttpRequestParsingStage()
    val requestParsingFlow: Flow[ByteString, HttpRequest, NotUsed] = Flow[ByteString].via(rootParser)
    requestParsingFlow
  }

  def rendering(): Flow[HttpResponse, ByteString, NotUsed] = {
    val identityFlow = Flow[HttpResponse]
    identityFlow.via(HttpResponseRenderer)
  }

  def bindAndHandle(handler: HttpRequest ⇒ HttpResponse, endpoint: InetSocketAddress)(implicit materializer: Materializer): Unit = {

    val identityFlow = Flow[HttpRequest]
    val mappedHandlerFlow: Flow[HttpRequest, HttpResponse, NotUsed] = identityFlow.map(handler)
    val handlerFlow: Flow[HttpRequest, HttpResponse, Future[Done]] = mappedHandlerFlow.watchTermination()(Keep.right)

    val renderingFlow: Flow[HttpResponse, ByteString, NotUsed] = rendering()
    val parsingFlow: Flow[ByteString, HttpRequest, NotUsed] = parsing()
    val parsingRendering: BidiFlow[HttpResponse, ByteString, ByteString, HttpRequest, NotUsed] = BidiFlow.fromFlows(renderingFlow, parsingFlow)

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

    val mappedAsyncSource: Source[Done, Future[TcpStream.ServerBinding]] = source.mapAsyncUnordered(1024)(
      (incomingConnection: TcpStream.IncomingConnection) ⇒ {
        val materializedValue: Future[Done] = finalFlow.join(incomingConnection.flow).run()
        materializedValue
      }
    )

    mappedAsyncSource.runWith(Sink.ignore) //this triggers first pull to start accepting connections.
  }
}
