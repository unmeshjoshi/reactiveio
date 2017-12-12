package com.reactive.http.parser

import akka.http.impl.util.ByteStringParserInput
import akka.http.scaladsl.model.{HttpMethod, IllegalUriException, Uri}
import akka.util.ByteString
import com.reactive.http.model
import com.reactive.http.model.HttpRequest

import scala.annotation.{switch, tailrec}

object parsing {

  sealed trait MessageOutput

  case object NeedsMoreData extends MessageOutput

  case class HttpMessage(request: HttpRequest) extends MessageOutput

  class ParsingException(val message: String = "") extends RuntimeException

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
}

class HttpRequestParser {
  private var state: ByteString ⇒ parsing.MessageOutput = startNewMessage(_, 0)

  protected final def continue(input: ByteString, offset: Int)(next: (ByteString, Int) ⇒ parsing.MessageOutput): parsing.MessageOutput = {
    state =
      math.signum(offset - input.length) match {
        case -1 ⇒
          val remaining = input.drop(offset)
          val stringToOutput: ByteString ⇒ parsing.MessageOutput = more ⇒ next(remaining ++ more, 0)
          stringToOutput
        case 0 ⇒ next(_, 0)
        case 1 ⇒ throw new IllegalStateException
      }
    parsing.NeedsMoreData
  }

  protected final def startNewMessage(input: ByteString, offset: Int): parsing.MessageOutput = {
    try {
      val result = parseMessage(input, offset)
      result
    }
    catch {
      case parsing.NotEnoughDataException ⇒ continue(input, offset)(startNewMessage)
    }
  }

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

    import akka.http.scaladsl.model.HttpMethods._
    (byteChar(input, cursor): @switch) match {
      case 'G' ⇒ parseMethod(GET)
      case _ ⇒ throw new RuntimeException("Invalid method")
    }
  }


  def parseBytes(input: ByteString): parsing.MessageOutput = {
    state(input)
  }

  def parseMessage(input: ByteString, offset: Int): parsing.MessageOutput = {
    var cursor = parseMethod(input, offset)
    cursor = parseRequestTarget(input, cursor)
    cursor = parseProtocol(input, cursor)
    HttpMessage(model.HttpRequest(method, uri))
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
        s"URI length exceeds the configured limit of $maxUriLength characters")

    val uriEnd = findUriEnd()
    try {
      val uriBytes = input.slice(uriStart, uriEnd)
      uri = Uri.parseHttpRequestTarget(new ByteStringParserInput(uriBytes))
    } catch {
      case IllegalUriException(info) ⇒ throw new ParsingException("invalid uri")
    }
    uriEnd + 1
  }


  var protocol: String = "HTTP/1.1"

  protected final def parseProtocol(input: ByteString, cursor: Int): Int = {
    def c(ix: Int) = byteChar(input, cursor + ix)

    if (c(0) == 'H' && c(1) == 'T' && c(2) == 'T' && c(3) == 'P' && c(4) == '/' && c(5) == '1' && c(6) == '.') {
      protocol = c(7) match {
        case '0' ⇒ "HTTP/1.0"
        case '1' ⇒ "HTTP/1.1"
        case _ ⇒ throw new ParsingException(s"Invalid protocol ${cursor}")
      }
      cursor + 8
    } else throw new ParsingException(s"Invalid protocol ${cursor}")
  }
}
