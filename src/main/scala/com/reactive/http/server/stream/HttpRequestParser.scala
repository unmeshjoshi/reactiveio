package com.reactive.http.server.stream

import akka.http.impl.util.ByteStringParserInput
import akka.http.scaladsl.model.StatusCodes.{BadRequest, RequestUriTooLong}
import akka.http.scaladsl.model._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString
import com.reactive.http.model
import com.reactive.http.model.HttpRequest
import com.reactive.http.parser.CharacterClasses
import com.reactive.http.parser.parsing.{NotEnoughDataException, ParsingException, byteChar}

import scala.annotation.{switch, tailrec}

class HttpRequestParser extends GraphStage[FlowShape[ByteString, HttpRequest]] { self ⇒

  trait RequestOutput
  case object NeedMoreData extends RequestOutput
  case object StreamEnd extends RequestOutput


  val in = Inlet[ByteString]("httpParser.in")
  val out = Outlet[HttpRequest]("httpParser.out")

  override def shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    setHandler(in, this)
    setHandler(out, this)

    override def onPush(): Unit = {
      val byteString = grab(in)
      val parserOutput = parseMessage(byteString)
      handleParserOutput(parserOutput)
    }

    override def onPull(): Unit = pull(in)

    private def handleParserOutput(output: Any): Unit = {
      output match {
        case StreamEnd ⇒ completeStage()
        case NeedMoreData ⇒ pull(in)
        case httpRequest: HttpRequest ⇒ push(out, httpRequest)
      }
    }

    var method: HttpMethod = _
    var uri: Uri = _

    import HttpMethods._

    def parseMessage(input: ByteString): HttpRequest = {
      var cursor = parseMethod(input, 0)
      cursor = parseRequestTarget(input, cursor)
      model.HttpRequest(method, uri)
    }

    def parseMethod(input: ByteString, cursor: Int): Int = {

      @tailrec def parseMethod(httpMethod: HttpMethod, ix: Int = 1): Int = {
        if (ix == httpMethod.value.length) {
          if (byteChar(input, cursor + ix) == ' ') {
            method = httpMethod
            cursor + ix + 1
          } else throw new RuntimeException("Invalid method")
        }
        else if (byteChar(input, cursor + ix) == httpMethod.value.charAt(ix)) parseMethod(httpMethod, ix + 1)
        else throw new RuntimeException("Invalid method")
      }

      (byteChar(input, cursor): @switch) match {
        case 'G' ⇒ parseMethod(GET)
        case 'P' ⇒ byteChar(input, cursor + 1) match {
          case 'O' ⇒ parseMethod(POST, 2)
          case 'U' ⇒ parseMethod(PUT, 2)
          case 'A' ⇒ parseMethod(PATCH, 2)
          case _ ⇒ throw new RuntimeException("Invalid method")
        }
        case 'D' ⇒ parseMethod(DELETE)
        case 'H' ⇒ parseMethod(HEAD)
        case 'O' ⇒ parseMethod(OPTIONS)
        case 'T' ⇒ parseMethod(TRACE)
        case 'C' ⇒ parseMethod(CONNECT)
        case _ ⇒ throw new RuntimeException("Invalid method")
      }
    }

    def parseRequestTarget(input: ByteString, cursor: Int): Int = {

      val uriStart = cursor
      val uriEndLimit = cursor + maxUriLength

      @tailrec def findUriEnd(ix: Int = cursor): Int = {
        if (ix == input.length) throw NotEnoughDataException
        else if (CharacterClasses.WSPCRLF(input(ix).toChar)) ix
        else if (ix < uriEndLimit) findUriEnd(ix + 1)
        else throw new ParsingException(
          s"URI length exceeds the configured limit of $maxUriLength characters")
      }

      val uriEnd = findUriEnd()
      try {
        val uriBytes = input.slice(uriStart, uriEnd)
        uri = Uri.parseHttpRequestTarget(new ByteStringParserInput(uriBytes), mode = Uri.ParsingMode.Strict)
      } catch {
        case IllegalUriException(info) ⇒ throw new ParsingException("illegal uri exception")
      }
      uriEnd + 1
    }

    val maxUriLength = 2048 //2k in akka settings
  }
}

