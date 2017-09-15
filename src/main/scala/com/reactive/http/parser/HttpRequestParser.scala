package com.reactive.http.parser

import akka.http.impl.util.ByteStringParserInput
import akka.http.scaladsl.model.StatusCodes.BadRequest
import akka.http.scaladsl.model.{HttpMethod, Uri}
import akka.util.ByteString
import com.reactive.http.model

import scala.annotation.{switch, tailrec}

package object parsing {

  class ParsingException(val message:String = "") extends RuntimeException

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
  sealed trait StateResult // phantom type for ensuring soundness of our parsing method setup
  final case class Trampoline(f: ByteString ⇒ StateResult) extends StateResult

  case object NotEnoughDataException extends RuntimeException

  private[this] var state: ByteString ⇒ model.HttpRequest = startNewMessage(_, 0)

  protected final def continue(input: ByteString, offset: Int)(next: (ByteString, Int) ⇒ model.HttpRequest): model.HttpRequest = {
    state =
      math.signum(offset - input.length) match {
        case -1 ⇒
          val remaining = input.drop(offset)
          more ⇒ next(remaining ++ more, 0)
        case 0 ⇒ next(_, 0)
        case 1 ⇒ throw new IllegalStateException
      }
    done()
  }

  private def done(): model.HttpRequest = null

  protected final def startNewMessage(input: ByteString, offset: Int): model.HttpRequest = {
    try {
      val result = parseMessage(input, offset)
      result
    }
    catch { case NotEnoughDataException ⇒ continue(input, offset)(startNewMessage) }
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


  def parseBytes(input:ByteString):model.HttpRequest = {
    state(input)
  }

  def parseMessage(input: ByteString, offset: Int): model.HttpRequest = {
    var cursor = parseMethod(input, offset)
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
        "URI length exceeds the configured limit of $maxUriLength characters")

    val uriEnd = findUriEnd()
    try {
      val uriBytes = input.slice(uriStart, uriEnd)
      uri = Uri.parseHttpRequestTarget(new ByteStringParserInput(uriBytes), mode = Uri.ParsingMode.Strict)
    } catch {
      case _ ⇒ throw new ParsingException(BadRequest.toString())
    }
    uriEnd + 1
  }
}
