package com.reactive.stream.impl

case object Empty
final class Connection(
                        var id:         Int,
                        var inOwner:    GraphStageLogic,
                        var outOwner:   GraphStageLogic,
                        var inHandler:  InHandler,
                        var outHandler: OutHandler) {
  var portState: Int = 1
  var slot: Any = Empty

  override def toString =
    s"Connection($id, $inOwner, $outOwner, $inHandler, $outHandler, $portState, $slot)"
}

case class Inlet(name:String) {
  private[stream] var id: Int = -1

}

case class Outlet(name:String) {
  private[stream] var id: Int = -1
}



trait InHandler {
  def onPush(elem:Any): Unit
}

trait OutHandler {
  def onPull(): Unit
}


trait Module {

  def attributes:Map[String, String]
  def shape:Shape
  final lazy val Inlets: Set[Inlet] = shape.inlets.toSet
  final lazy val Outlets: Set[Outlet] = shape.outlets.toSet

  def isRunnable: Boolean = Inlets.isEmpty && Outlets.isEmpty
  final def isSink: Boolean = (Inlets.size == 1) && Outlets.isEmpty
  final def isSource: Boolean = (Outlets.size == 1) && Inlets.isEmpty
  final def isFlow: Boolean = (Inlets.size == 1) && (Outlets.size == 1)
  final def isBidiFlow: Boolean = (Inlets.size == 2) && (Outlets.size == 2)
  def isAtomic: Boolean = subModules.isEmpty
  def isCopied: Boolean = false
  def isFused: Boolean = false

  def subModules: Set[Module]

  final def isSealed: Boolean = isAtomic || isCopied || isFused || attributes.nonEmpty

  def downstreams: Map[Outlet, Inlet] = Map.empty
  def upstreams: Map[Inlet, Outlet] = Map.empty


  def fuse(that: Module, from: Outlet, to: Inlet) = {
    this.compose(that).wire(from, to)
  }

  final def wire(from: Outlet, to: Inlet): Module = {

    require(
      Outlets(from),
      if (downstreams.contains(from)) s"The output port [$from] is already connected"
      else s"The output port [$from] is not part of the underlying graph.")
    require(
      Inlets(to),
      if (upstreams.contains(to)) s"The input port [$to] is already connected"
      else s"The input port [$to] is not part of the underlying graph.")

    CompositeModule(
      if (isSealed) Set(this) else subModules,
      Shape(shape.inlets.filterNot(_ == to), shape.outlets.filterNot(_ == from)),
      downstreams.updated(from, to),
      upstreams.updated(to, from),
      if (isSealed) Map() else attributes)
  }

  def compose[A, B, C](that: Module): Module = {
    val modulesLeft = if (this.isSealed) Set(this) else this.subModules
    val modulesRight = if (that.isSealed) Set(that) else that.subModules

    CompositeModule(
      modulesLeft union modulesRight,
      Shape(shape.inlets ++ that.shape.inlets, shape.outlets ++ that.shape.outlets),
      downstreams ++ that.downstreams,
      upstreams ++ that.upstreams,
      Map())
  }
}

abstract class AtomicModule extends Module {
  final override def subModules: Set[Module] = Set.empty
  final override def downstreams: Map[Outlet, Inlet] = super.downstreams
  final override def upstreams: Map[Inlet, Outlet] = super.upstreams
}

final case class GraphStageModule(
                                   shape:      Shape,
                                   attributes: Map[String, String],
                                   stage:      StreamStage) extends AtomicModule


final case class CompositeModule(
                                  override val subModules:                   Set[Module],
                                  override val shape:                        Shape,
                                  override val downstreams:                  Map[Outlet, Inlet],
                                  override val upstreams:                    Map[Inlet, Outlet],
                                  override val attributes:                   Map[String, String]) extends Module


class SinkStage extends StreamStage {
  val in = Inlet("In")
  override val shape: Shape = new Shape(List(in), List())
  val module = new GraphStageModule(shape, Map(), this)

  def createLogic(): GraphStageLogic = {
    new GraphStageLogic(shape.inlets.size, shape.outlets.size) with InHandler {
      override def onPush(elem:Any): Unit = {
        println(elem)
      }
    }
  }
}

class SourceStage extends StreamStage {
  val out = Outlet("Out")
  override val shape = new Shape(List(), List(out))

  val module = new GraphStageModule(shape, Map(), this)

  def createLogic():GraphStageLogic = {
    new GraphStageLogic(shape.inlets.size, shape.outlets.size) with OutHandler  {
      override def onPull(): Unit = {
        push(out, 1)
      }
    }

  }
}


case class Shape(inlets:Seq[Inlet], outlets:Seq[Outlet])


class Materializer {

}

class GraphAssembly(val stages:             Array[SourceStage],
                    val originalAttributes: Array[String],
                    val ins:                Array[Inlet],
                    val inOwners:           Array[Int],
                    val outs:               Array[Outlet],
                    val outOwners:          Array[Int]) {
  val connectionCount = ins.length

}

object GraphInterpreter {
  final val Debug = true

  final val NoEvent = null
  final val Boundary = -1

  final val InReady = 1
  final val Pulling = 2
  final val Pushing = 4
  final val OutReady = 8

  final val InClosed = 16
  final val OutClosed = 32
  final val InFailed = 64

  final val PullStartFlip = 3 // 0011
  final val PullEndFlip = 10 // 1010
  final val PushStartFlip = 12 //1100
  final val PushEndFlip = 5 //0101

  final val KeepGoingFlag = 0x4000000
  final val KeepGoingMask = 0x3ffffff

}

class GraphInterpreter(assembly:GraphAssembly) {


  import GraphInterpreter._

  private def queueStatus: String = {
    val contents = (queueHead until queueTail).map(idx ⇒ {
      val conn = eventQueue(idx & mask)
      conn
    })
    s"(${eventQueue.length}, $queueHead, $queueTail)(${contents.mkString(", ")})"
  }
  private[this] var _Name: String = _
  def Name: String =
    if (_Name eq null) {
      _Name = f"${System.identityHashCode(this)}%08X"
      _Name
    } else _Name

  private[this] val eventQueue = Array.ofDim[Connection](1 << (32 - Integer.numberOfLeadingZeros(assembly.connectionCount - 1)))
  private[this] val mask = eventQueue.length - 1
  private[this] var queueHead: Int = 0
  private[this] var queueTail: Int = 0

  private[this] var chaseCounter = 0 // the first events in preStart blocks should be not chased
  private[this] var chasedPush: Connection = NoEvent
  private[this] var chasedPull: Connection = NoEvent

  private[stream] def chasePull(connection: Connection): Unit = {
    if (chaseCounter > 0 && chasedPull == NoEvent) {
      chaseCounter -= 1
      chasedPull = connection
    } else enqueue(connection)
  }

  private[stream] def chasePush(connection: Connection): Unit = {
    if (chaseCounter > 0 && chasedPush == NoEvent) {
      chaseCounter -= 1
      chasedPush = connection
    } else enqueue(connection)
  }

  def setHandler(connection: Connection, handler: InHandler): Unit = {
    connection.inHandler = handler
  }

  /**
    * Dynamic handler changes are communicated from a GraphStageLogic by this method.
    */
  def setHandler(connection: Connection, handler: OutHandler): Unit = {
    connection.outHandler = handler
  }



  private def dequeue(): Connection = {
    val idx = queueHead & mask
    val elem = eventQueue(idx)
    eventQueue(idx) = NoEvent
    queueHead += 1
    elem
  }
  def enqueue(connection: Connection): Unit = {
    if (queueTail - queueHead > mask) new Exception(s"$Name internal queue full ($queueStatus) + $connection").printStackTrace()
    eventQueue(queueTail & mask) = connection
    queueTail += 1
  }

  val materializer: Materializer = ???

}

abstract class GraphStageLogic(val inCount: Int, val outCount: Int) {
  import GraphInterpreter._

  private val handlers = Array.ofDim[Any](inCount + outCount)
  private val portToConn = Array.ofDim[Connection](handlers.length)

  private def conn(in: Inlet): Connection = portToConn(in.id)
  private def conn(out: Outlet): Connection = portToConn(out.id + inCount)

  private[this] var _interpreter: GraphInterpreter = _


  private def interpreter_=(gi: GraphInterpreter) = _interpreter = gi

  private def interpreter: GraphInterpreter =
    if (_interpreter == null)
      throw new IllegalStateException("not yet initialized: only setHandler is allowed in GraphStageLogic constructor")
    else _interpreter

  final protected def setHandler(in: Inlet, handler: InHandler): Unit = {
    handlers(in.id) = handler
    if (_interpreter != null) _interpreter.setHandler(conn(in), handler)
  }

  final protected def setHandler(out: Outlet, handler: OutHandler): Unit = {
    handlers(out.id + inCount) = handler
    if (_interpreter != null) _interpreter.setHandler(conn(out), handler)
  }

  final protected def pull[T](in: Inlet): Unit = {
    val connection = conn(in)
    val it = interpreter
    val portState = connection.portState

    if ((portState & (InReady)) == InReady) {
      connection.portState = portState // mark pull start^ PullStartFlip
      it.chasePull(connection)
    } 
  }

  final protected def push[T](out: Outlet, elem: T): Unit = {
    val connection = conn(out)
    val it = interpreter
    val portState = connection.portState

    connection.portState = portState ^ PushStartFlip

    if ((portState & (OutReady | OutClosed | InClosed)) == OutReady && (elem != null)) {
      connection.slot = elem
      it.chasePush(connection)
    } else {
      // Restore state for the error case
      connection.portState = portState
      // No error, just InClosed caused the actual pull to be ignored, but the status flag still needs to be flipped
      connection.portState = portState ^ PushStartFlip
    }
  }
}
