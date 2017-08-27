package com.reactive.http.server.stream

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString


object HttpResponseRenderer extends GraphStage[FlowShape[Any, ByteString]] {
  val in = Inlet[Any]("HttpResponseRenderer.in")
  val out = Outlet[ByteString]("HttpResponseRenderer.out")
  val shape: FlowShape[Any, ByteString] = FlowShape(in, out)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      setHandler(in, new InHandler {

        def render(value: Any): Unit = {
          push(out, ByteString("Hellow Streaming World!")) //this is where the the response is renderered.
        }


        override def onPush(): Unit = render(grab(in))
      })
      val waitForDemandHandler = new OutHandler {
        def onPull(): Unit = pull(in)
      }
      setHandler(out, waitForDemandHandler)
    }
}