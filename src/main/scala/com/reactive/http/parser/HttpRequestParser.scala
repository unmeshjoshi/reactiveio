package com.reactive.http.parser

import akka.event.LoggingAdapter
import akka.http.impl.util.ByteStringParserInput
import akka.http.scaladsl.model.StatusCodes.{BadRequest, RequestUriTooLong}
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ParserSettings
import akka.util.ByteString
import com.reactive.http.model

import scala.annotation.{switch, tailrec}

package object parsing {

  class ParsingException(val status: StatusCode,
                          val info: ErrorInfo) extends RuntimeException(info.formatPretty) {
    def this(status: StatusCode, summary: String = "") =
      this(status, ErrorInfo(if (summary.isEmpty) status.defaultMessage else summary))

    def this(summary: String) =
      this(StatusCodes.BadRequest, ErrorInfo(summary))
  }

  object NotEnoughDataException extends RuntimeException

  def escape(c: Char): String = c match {
    case '\t' ⇒ "\\t"
    case '\r' ⇒ "\\r"
    case '\n' ⇒ "\\n"
    case x if Character.isISOControl(x) ⇒ "\\u%04x" format c.toInt
    case x ⇒ x.toString
  }

  def byteChar(input: ByteString, ix: Int): Char = byteAt(input, ix).toChar

  def byteAt(input: ByteString, ix: Int): Byte =
    if (ix < input.length) input(ix) else throw NotEnoughDataException

  def logParsingError(info: ErrorInfo, log: LoggingAdapter,
                      setting: ParserSettings.ErrorLoggingVerbosity): Unit =
    setting match {
      case ParserSettings.ErrorLoggingVerbosity.Off ⇒ // nothing to do
      case ParserSettings.ErrorLoggingVerbosity.Simple ⇒ log.warning(info.summary)
      case ParserSettings.ErrorLoggingVerbosity.Full ⇒ log.warning(info.formatPretty)
    }
}

class HttpRequestParser {

  import parsing._

  var method: HttpMethod = _
  var uri: Uri = _

  def parseMethod(input: ByteString, cursor: Int): Int = {
    @tailrec def parseMethod(meth: HttpMethod, ix: Int = 1): Int =
      if (ix == meth.value.length)
        if (byteChar(input, cursor + ix) == ' ') {
          method = meth
          cursor + ix + 1
        } else throw new RuntimeException("Invalid method")
      else if (byteChar(input, cursor + ix) == meth.value.charAt(ix)) parseMethod(meth, ix + 1)
      else throw new RuntimeException("Invalid method")

    import HttpMethods._
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

  def parseMessage(input: ByteString): model.HttpRequest = {
    var cursor = parseMethod(input, 0)
    cursor = parseRequestTarget(input, cursor)
    model.HttpRequest(method, uri)
  }

  val maxUriLength = 2048 //2k in akka settings

  def parseRequestTarget(input: ByteString, cursor: Int): Int = {

    val uriStart = cursor
    val uriEndLimit = cursor + maxUriLength

    @tailrec def findUriEnd(ix: Int = cursor): Int =
      if (ix == input.length) throw NotEnoughDataException
      else if (CharacterClasses.WSPCRLF(input(ix).toChar)) ix
      else if (ix < uriEndLimit) findUriEnd(ix + 1)
      else throw new ParsingException(
        RequestUriTooLong,
        "URI length exceeds the configured limit of $maxUriLength characters")

    val uriEnd = findUriEnd()
    try {
      val uriBytes = input.slice(uriStart, uriEnd)
      uri = Uri.parseHttpRequestTarget(new ByteStringParserInput(uriBytes), mode = Uri.ParsingMode.Strict)
    } catch {
      case IllegalUriException(info) ⇒ throw new ParsingException(BadRequest, info)
    }
    uriEnd + 1
  }
}
