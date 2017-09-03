package com.reactive.http.server.stream

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString


object HttpResponseRendererStale extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in = Inlet[ByteString]("HttpResponseRenderer.in")
  val out = Outlet[ByteString]("HttpResponseRenderer.out")
  val shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(in, new InHandler {

        def render(value: ByteString): Unit = {
          println(s"Writing ${value}")
          push(out, value) //this is where the the response is renderered.
          completeStage()
        }


        override def onPush(): Unit = render(grab(in))
      })
      val waitForDemandHandler = new OutHandler {
        def onPull(): Unit = pull(in)
      }
      setHandler(out, waitForDemandHandler)
    }
}