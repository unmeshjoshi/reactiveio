import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.util.ByteString
import com.reactive.http.parser.{HttpRequestParser, parsing}
import com.reactive.http.server.actor._

//this represents a TcpStreamLogic graph stage. Handles reading from and writing to connection based on pull/push
class TcpConnectionHandler(connection: ActorRef, remoteAddress: InetSocketAddress) extends Actor {
  val parser = new HttpRequestParser()
  override def preStart(): Unit = {
    connection ! Register(self, keepOpenOnPeerClosed = true, useResumeWriting = false)
  }

  override def receive: Receive = {
    case Received(data) ⇒
      val messageOutput = parser.parseBytes(data)
      messageOutput match {
        case parsing.NeedsMoreData ⇒
        case m:parsing.HttpMessage ⇒ {
          println(s"Read http request $messageOutput")
          val responseText = "Hello Actor Based Server!"
          val response =
            """HTTP/1.1 200 OK
              |Server: akka-http/1.0.0
              |Date: Thu, 25 Aug 2011 09:10:29 GMT
              |Content-Length: ${responseText.length}
              |
              |${responseText}""".stripMargin.replace("\n", "\r\n")

          connection ! Write(ByteString(response))
          connection ! CloseCommand
        }
      }
  }
}


//Equivalent of ConnectionSourceStage
class ServerActor(val endpoint: InetSocketAddress) extends Actor {
  val tcpManager = context.actorOf(Props(new TcpManager), "tcpManager") //TODO: this should be moved to actorsystem extension
  var listener: ActorRef = _
  var numberOfConnections = 0

  override def preStart(): Unit = {
    tcpManager ! Bind(self, endpoint) //self sent to
  }

  override def receive: Receive = {
    case Bound(address) ⇒
      println("Bound")
      listener = sender()
      println(s"listner set to ${listener}")

    case Connected(remoteAddress, localAddress) ⇒
      val connection = sender()
      numberOfConnections += 1
      context.actorOf(Props(new TcpConnectionHandler(connection, remoteAddress)), s"tcpConnectionhander${numberOfConnections}")
      println(s"listner is ${listener}")
      listener ! ResumeAccepting(1)
    case a@_ ⇒ println(s"_______________Unhandled message_______________${a}")
  }
}
//
object ActorBasedNIOServer extends App {
  val system = ActorSystem("Server")
  system.actorOf(Props(new ServerActor(new InetSocketAddress("localhost", 5555))), "ServerActor")
}

