package com.reactive.http.parser

import java.io.{ByteArrayInputStream, InputStream}

import org.scalatest.FunSuite

class BlockingHttpParserTest extends FunSuite {

  def parseMethod(is:InputStream): String = {
    def readByte = {
      is.read().toByte
    }

    var sb = Array[Byte]()
    var i:Byte = readByte
    while(i != ' ') {
      sb = sb :+ i
      i = readByte
    }
    new String(sb, "ASCII")
  }

  test("should parse HTTP method") {
    val message: String = "GET /resource HTTP/1.1"
    val method = parseMethod(new ByteArrayInputStream(message.getBytes))
    assert(method == "GET")
  }
}
