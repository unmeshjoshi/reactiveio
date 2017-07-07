package com.reactive.http.model

import akka.http.scaladsl.model.{HttpMethod, Uri}

case class HttpRequest(val method:HttpMethod, target:Uri)
