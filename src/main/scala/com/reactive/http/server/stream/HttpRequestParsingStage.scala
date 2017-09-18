package com.reactive.http.server.stream

import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.reactive.http.model.HttpRequest
import com.reactive.http.parser.{HttpRequestParser, parsing}

class HttpRequestParsingStage extends GraphStage[FlowShape[ByteString, HttpRequest]] { self ⇒

  val in = Inlet[ByteString]("httpParser.in")
  val out = Outlet[HttpRequest]("httpParser.out")

  override def shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    val httpParser:HttpRequestParser = new HttpRequestParser() //maintains state for this connection

    setHandler(in, this)
    setHandler(out, this)

    override def onPush(): Unit = {
      val bytes = grab(in)
      val parserOutput = httpParser.parseBytes(bytes)
      handleParserOutput(parserOutput)
    }

    override def onPull(): Unit = pull(in)

    private def handleParserOutput(output: parsing.MessageOutput): Unit = {
      output match {
        case parsing.NeedsMoreData ⇒ pull(in)
        case m:parsing.HttpMessage ⇒ push(out, m.request)
      }
    }
  }
}

