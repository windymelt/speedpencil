package example

import shared.Protocol

class InternalCanvas {

  private var buffer =
    Array.fill(
      InternalCanvas.bufferWidthCount * InternalCanvas.bufferHeightCount
    )(
      InternalCanvas.genBlock()
    )
  private var composeBuffer = InternalCanvas.genBlock()

  def getBlock(n: Int, m: Int): Array[Int] = buffer(
    InternalCanvas.getBlockIdx(n, m)
  )
  def multiplyBlock(n: Int, m: Int, block: Array[Int]): Array[Int] = {
    this.getBlock(n, m) multiply block
    this.composeBuffer copyToArray this.getBlock(n, m)
    this.composeBuffer
  }
  def foreachCell(f: (Int, Int, Array[Int]) => _): Unit = {
    for (idx <- 0 until buffer.size) {
      val n = idx / InternalCanvas.bufferHeightCount
      val m = idx % InternalCanvas.bufferHeightCount
      f(n, m, this.getBlock(n, m))
    }
  }
  def clear(): Unit = {
    val emptyBlock = InternalCanvas.genBlock()
    for (b <- buffer) {
      emptyBlock copyToArray b
    }
  }

  implicit class ArrayToMultiplication(arr: Array[Int]) {
    def multiply(arr2: Array[Int]): Array[Int] = {
      arr.copyToArray(composeBuffer)
      for (i <- 0 until composeBuffer.size) {
        composeBuffer(i) *= arr2(i)
      }
      for (i <- 0 until composeBuffer.size) {
        composeBuffer(i) /= 255
      }
      composeBuffer
    }
  }
}

object InternalCanvas {
  val cellSize = shared.Protocol.blockSize * shared.Protocol.blockSize * 4
  val bufferWidthCount = (Protocol.canvasWidth / Protocol.blockSize)
  val bufferHeightCount = (Protocol.canvasHeight / Protocol.blockSize)
  private def getBlockIdx(n: Int, m: Int): Int = n * bufferHeightCount + m
  private def genBlock() = Array.fill(cellSize)(255)
}
