package com.reactive.http.server

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.spi.{AbstractSelector, SelectorProvider}
import java.nio.channels.{SelectionKey, ServerSocketChannel, SocketChannel}

import akka.util.ByteString
import com.reactive.http.SampleResponse
import com.reactive.http.model.HttpRequest
import com.reactive.http.parser.{HttpRequestParser, parsing}

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
    val httpParser = new HttpRequestParser()
    socketChannel.register(selector, SelectionKey.OP_READ, httpParser)
  }

  def read(key: SelectionKey) = {
    val socketChannel = key.channel().asInstanceOf[SocketChannel]
    val byteString = readFromSocket(socketChannel)
    val httpParser = key.attachment().asInstanceOf[HttpRequestParser]

    val messageOutput = httpParser.parseBytes(byteString)
    messageOutput match {
      case parsing.NeedsMoreData ⇒ {
        socketChannel.register(selector, SelectionKey.OP_READ, httpParser)

      }
      case m:parsing.HttpMessage ⇒ {
        println(s"Read http request $messageOutput")
        socketChannel.register(selector, SelectionKey.OP_WRITE, m.request)
       }
    }
  }

  private def readFromSocket(socketChannel: SocketChannel) = {
    val buffer = ByteBuffer.allocate(1024)
    val bytesRead = socketChannel.read(buffer)
    buffer.flip()
    ByteString(buffer)
  }


  def write(key: SelectionKey) = {
    val socketChannel = key.channel().asInstanceOf[SocketChannel]
    val httpRequest = key.attachment().asInstanceOf[HttpRequest]

    println(s"Writing response for ${httpRequest}")


    val responseText: String = SampleResponse.json

    val response =
      s"""HTTP/1.1 200 OK
        |Server: akka-http/1.0.0
        |Date: Thu, 25 Aug 2011 09:10:29 GMT
        |Content-Length: ${responseText.length}
        |
        |${responseText}""".stripMargin.replace("\n", "\r\n")

    val responseBytes = response.getBytes("UTF-8")
    val bytesWritten = socketChannel.write(ByteBuffer.wrap(responseBytes))
    if (bytesWritten < responseBytes.length) {
      val remainingBytes = responseBytes.drop(bytesWritten)

    }
    socketChannel.close()
  }
}
