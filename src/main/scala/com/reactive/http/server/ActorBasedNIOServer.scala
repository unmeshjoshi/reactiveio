import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import com.reactive.http.server.actor.ServerActor

object Server extends App {
  val system = ActorSystem("Server")
  system.actorOf(Props(new ServerActor(new InetSocketAddress("localhost", 5555))))
}

