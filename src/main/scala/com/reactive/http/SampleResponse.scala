package com.reactive.http

import com.reactive.http.model.Customer
import play.api.libs.json.Json

object SampleResponse {
  def json = {
    var n = 1
    var _2kbAddress = "m" * 2048

    _2kbAddress = _2kbAddress + "  Lexington, MA 02457"
    val customer = Customer(s"Test User", "08/08/1976", s"${_2kbAddress}")

    val start = System.currentTimeMillis()
    val responseText = Json.toJson(customer).toString()
    val end = System.currentTimeMillis()

    println(s"Took ${end - start} ms")
    responseText
  }

}
