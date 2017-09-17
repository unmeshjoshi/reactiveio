package com.reactive.http.parser

import akka.http.scaladsl.model.HttpMethods
import akka.util.ByteString
import com.reactive.http.model.HttpRequest
import com.reactive.http.parser.parsing.HttpMessage
import org.scalatest.FunSuite


class HttpRequestParserTest extends FunSuite {

  test("parse simple GET request") {
    val parser = new HttpRequestParser()

    val message: String = "GET /resource HTTP/1.1"
    var request: parsing.MessageOutput = parser.parseBytes(ByteString(message))
    assert(request == HttpMessage(HttpRequest(HttpMethods.GET, "/resource")))
  }

  test("returns NeedsModeData if request is incomplete") {
    val parser = new HttpRequestParser()

    val message: String = "GET /resource HTTP/1.1"
    val bytes = message.toCharArray.map(_.toString).toSeq
    var request: parsing.MessageOutput = parser.parseBytes(ByteString(bytes(0)))
    assert(request == parsing.NeedsMoreData)
  }

  test("parse GET request byte by byte") {
    val parser = new HttpRequestParser()

    val message: String = "GET /resource HTTP/1.1"
    val bytes = message.toCharArray.map(_.toString).toSeq
    var request: parsing.MessageOutput = parsing.NeedsMoreData
    var index = 0
    while(request == parsing.NeedsMoreData) {
      request = parser.parseBytes(ByteString(bytes(index)))
      index = index + 1
    }

    assert(request == HttpMessage(HttpRequest(HttpMethods.GET, "/resource")))
  }
}
