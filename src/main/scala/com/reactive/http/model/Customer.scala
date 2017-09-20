package com.reactive.http.model

import play.api.libs.json.{Json, OFormat}

case class Customer(name:String, birthDate:String, address:String) {

}

object Customer {
  implicit val format: OFormat[Customer] = Json.format[Customer]
}