package com.reactive.graph

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink}

object BroadcastExample extends App {
  import Models._

  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  val writeAuthors: Sink[Author, NotUsed] = Sink.foreach(println).asInstanceOf[Sink[Author, NotUsed]]
  val writeHashtags: Sink[Hashtag, NotUsed] = Sink.foreach(println).asInstanceOf[Sink[Hashtag, NotUsed]]

  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    val bcast = b.add(Broadcast[Tweet](2))
    ClosedShape
  })
  g.run()
}
