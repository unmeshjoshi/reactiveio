package com.reactive.http.server.stream

import java.net.InetSocketAddress

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep}
import akka.util.ByteString

import scala.concurrent.Future

object TcpStream {

  final case class ServerBinding(localAddress: InetSocketAddress)(private val unbindAction: () ⇒ Future[Unit]) {
    def unbind(): Future[Unit] = unbindAction()
  }

  final case class IncomingConnection(localAddress: InetSocketAddress,
                                       remoteAddress: InetSocketAddress,
                                       flow: Flow[ByteString, ByteString, NotUsed]) {

    def handleWith[Mat](handler: Flow[ByteString, ByteString, Mat])(implicit materializer: Materializer): Mat =
      flow.joinMat(handler)(Keep.right).run()

  }

}
