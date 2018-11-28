package com.reactive.stream.impl

import org.scalatest.FunSuite

class StreamStageMaterializationTest extends FunSuite {

  test("Should fuse graph stages and materialise") {
    val source = new SourceStage
    val sink = new SinkStage

    val module = source.module.fuse(sink.module, source.module.shape.outlets.head, sink.module.shape.inlets.head)
    println(module)
  }

}
