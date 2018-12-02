package com.reactive.http.client

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try

object ClientApp extends App {
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val actorSystem = ActorSystem("client")
  implicit val mat = ActorMaterializer()
  private val pool: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), NotUsed] = Http.get(actorSystem).superPool[Int]()

  val httpRequest = HttpRequest()

  private val responseFlow: Source[(Try[HttpResponse], Int), NotUsed] = Source(0 to 20000)
    .map(i => (HttpRequest(uri = Uri("http://localhost:5555")), i)).via(pool)

  private val eventualTuples: Future[immutable.IndexedSeq[(Try[HttpResponse], Int)]] = responseFlow.runWith(Sink.collection)
  eventualTuples.onComplete(elem ⇒ println(elem))
}
