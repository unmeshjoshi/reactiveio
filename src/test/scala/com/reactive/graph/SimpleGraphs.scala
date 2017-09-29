package com.reactive.graph

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

object SimpleGraphs extends App {

  class SourceGraphStage extends GraphStage[SourceShape[Int]] {
    val out = Outlet[Int]("SourceGraph.out")
    override val shape: SourceShape[Int] = SourceShape(out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with OutHandler {
      var nextInt = 1
      override def onPull(): Unit = {
        push(out, nextInt)
        nextInt  = nextInt + 1
      }
    }
  }


  class SinkGraphStage extends GraphStage[SinkShape[Int]] {
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with InHandler {
      override def onPush(): Unit = {
        val pushedInt = grab(in)
        println(pushedInt)
        pull(in)
      }
    }

    val in: Inlet[Int] = Inlet[Int]("SinkGraph.in")

    override def shape: SinkShape[Int] = SinkShape(in)
  }

  val connections = List()
  final class Connection(
                          var id:         Int,
                          var inOwner:    GraphStageLogic,
                          var outOwner:   GraphStageLogic,
                          var inHandler:  InHandler,
                          var outHandler: OutHandler) {

    override def toString =
       s"Connection($id, $inOwner, $outOwner, $inHandler, $outHandler)"
  }

  val sink = new SinkGraphStage()
  val sinkLogic = sink.createLogic(Attributes())



  val source = new SourceGraphStage()
  val sourceLogic = source.createLogic(Attributes())

  private val connection = new Connection(1, sinkLogic, sourceLogic, sinkLogic.asInstanceOf[InHandler], sourceLogic.asInstanceOf[OutHandler])



  processPull(connection)
  processPush(connection)


  def processPull(connection: Connection) = {
    connection.outHandler.onPull()
  }
  def processPush(connection: Connection) = {
    connection.inHandler.onPush()
  }

}




