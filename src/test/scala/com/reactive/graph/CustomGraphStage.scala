package com.reactive.graph

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.{Done, NotUsed}

import scala.concurrent.Future


class CustomGraphStage[T] extends GraphStage[FlowShape[T, T]] {
  val in = Inlet[T]("AccumulateWhileUnchanged.in")
  val out = Outlet[T]("AccumulateWhileUnchanged.out")

  override def shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogic(shape) {

      setHandlers(in, out, new InHandler with OutHandler {

        override def onPush(): Unit = {
          val e = new Exception()
          e.printStackTrace()
          println("Called out onPush")
          val nextElement = grab(in)
          push(out, nextElement)
        }

        override def onPull(): Unit = {
          val e = new Exception()
          e.printStackTrace()
          println("Called out onPull")
          pull(in)
        }
      })
    }
  }
}

object SimpleGraphApp extends App {
  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()
  val source: Source[Int, NotUsed] = Source(1 to 100)
  val done: Future[Done] = source.via(new CustomGraphStage[Int]).runForeach(i => println(i))(materializer)
  implicit val ec = system.dispatcher
  done.onComplete(_ => system.terminate())
}
