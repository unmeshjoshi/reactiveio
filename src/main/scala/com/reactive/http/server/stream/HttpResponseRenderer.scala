package com.reactive.http.server.stream

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import com.reactive.http.model.HttpResponse

object HttpResponseRenderer extends GraphStage[FlowShape[HttpResponse, ByteString]] {
  override def shape = FlowShape(in, out)
  private val in: Inlet[HttpResponse] = Inlet[HttpResponse]("HttpResponseRenderer.in")
  private val out: Outlet[ByteString] = Outlet[ByteString]("HttpResponseRenderer.out")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {
    new GraphStageLogic(shape) {

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val httpResponse: HttpResponse = grab(in)
          val responseText = httpResponse.response
          Thread.sleep(10) // to simulate some processing
          val response =
            s"""HTTP/1.1 200 OK
              |Server: akka-http/1.0.0
              |Date: Thu, 25 Aug 2011 09:10:29 GMT
              |Content-Length: ${responseText.length}
              |
              |${responseText}""".stripMargin.replace("\n", "\r\n")
          val resultValue = ByteString(response)
          push(out, resultValue) // this is where the response is rendered
          completeStage()
        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
    }
  }
}
