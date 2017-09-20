package com.reactive.http

import com.reactive.http.model.Customer
import play.api.libs.json.Json

object SampleResponse {
  def json = {
    var n = 1
    var address = "m" * 2048

    address = address + "  Lexington, MA 02457"
    val customer = Customer(s"Test User", "08/08/1976", s"${address}")

    val start = System.currentTimeMillis()
    val responseText = Json.toJson(customer).toString()
    val end = System.currentTimeMillis()

    println(s"Took ${end - start} ms")
    responseText
  }

}
