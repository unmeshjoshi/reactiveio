# reactiveio

This is a thin slice of a sample http server using Akka streams. We have copied code snippets from Akka source code.
This is an example aimed at training/learning Akka Http, Akka Streams, Akka IO and Java NIO
I am hoping that this thin slice will help developers understand how Akka Http, Akka Streams, Akka IO and Java NIO are layered and integrate.
The project has three Http Servers which are very very mininal and handle only GET request to return a 200 OK Hello World response.
* SingleThreadedNIOServer
  This is a basic single threaded Http Server based on Java NIO
* ActorBasedNIOServer
  This is a Actor based Http Server based on Java NIO. This very similar to the way Akka IO works
* StreamBasedNIOServer
  This is a very simple Http Server written using Akka Streams, on top of ActorBasedNIOServer. This demonstrates how Akka Http is implemented.
  
  
  
