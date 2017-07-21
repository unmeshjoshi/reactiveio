package com.reactive.http.server

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.spi.{AbstractSelector, SelectorProvider}
import java.nio.channels.{SelectionKey, ServerSocketChannel, SocketChannel}

import akka.util.ByteString
import com.reactive.http.parser.HttpRequestParser

import scala.annotation.tailrec


object SingleThreadedNIOServer extends App {

  val serverSocketChannel: ServerSocketChannel = ServerSocketChannel.open
  serverSocketChannel.configureBlocking(false)
  val provider: SelectorProvider = SelectorProvider.provider()
  val selector: AbstractSelector = provider.openSelector()

  val channel: ServerSocketChannel = serverSocketChannel.bind(new InetSocketAddress("localhost", 5555))
  val key: SelectionKey = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT)

  println("Bound to localhost:5555")

  runEventLoop()

  @tailrec
  def runEventLoop(): Unit = {
    if (selector.select() > 0) {
      val keys = selector.selectedKeys()
      val iterator = keys.iterator()
      while (iterator.hasNext) {
        val key = iterator.next()
        if (key.isAcceptable) {
          accept(key)
        } else if (key.isReadable) {
          read(key)
        } else if (key.isWritable) {
          write(key)
        }
      }
      keys.clear() // we need to remove the selected keys from the set, otherwise they remain selected
    }
    runEventLoop()
  }

  def accept(key: SelectionKey) = {
    println("Accepting")
    val selectableChannel = key.channel().asInstanceOf[ServerSocketChannel]
    val socketChannel = selectableChannel.accept()
    socketChannel.configureBlocking(false)
    socketChannel.register(selector, SelectionKey.OP_READ)
  }

  def read(key: SelectionKey) = {
    val socketChannel = key.channel().asInstanceOf[SocketChannel]
    val byteString = readFromSocket(socketChannel)

    val httpRequest = new HttpRequestParser().parseMessage(byteString) //TODO: make httprequestparser stateful
    println(s"Read http request $httpRequest")

    socketChannel.register(selector, SelectionKey.OP_WRITE, httpRequest)
  }

  private def readFromSocket(socketChannel: SocketChannel) = {
    val buffer = ByteBuffer.allocate(1024)
    val bytesRead = socketChannel.read(buffer)
    buffer.flip()
    ByteString(buffer)
  }

  def write(key: SelectionKey) = {
    val socketChannel = key.channel().asInstanceOf[SocketChannel]
    val httpRequest = key.attachment().asInstanceOf[com.reactive.http.model.HttpRequest]

    println(s"Writing response for ${httpRequest}")

//    val response = s"Hello NIO World from ${httpRequest.target} \r\n"
    val response = "HTTP/1.1 200 OK\r\n Content-Length: 38\r\n Content-Type: text/html\r\n \r\n <html><body>Hello World!</body></html>"
    socketChannel.write(ByteBuffer.wrap(response.getBytes("UTF-8")))
    socketChannel.close()
  }
}