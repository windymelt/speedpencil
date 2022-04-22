package frontend

import org.scalajs.dom.raw._
import org.scalajs.dom
import shared.Protocol

object Frontend {
  var canvas: HTMLCanvasElement = null
  var ctx: CanvasRenderingContext2D = null
  var ws: WebSocket = null

  val blockSize = shared.Protocol.blockSize
  val blockSizeD: Double = shared.Protocol.blockSize.toDouble

  def main(args: Array[String]): Unit = {
    println("hello, javascript and browser!")

    canvas =
      dom.document.getElementById("canvas").asInstanceOf[HTMLCanvasElement]
    ctx = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
    ctx.fillStyle = "rgb(255,255,255)"
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    blockBuffer = ctx.createImageData(blockSize, blockSize)
    ctx.fillText("Hello world from scala.js!", 320, 240)

    ctx.strokeStyle = "rgba(0, 128, 128, 200)"

    // ctx.strokeStyle = "rgb(200,200,200)"
    // for {
    //   x <- 0 to canvas.width by blockSize
    // } yield {
    //   ctx.moveTo(x, 0)
    //   ctx.lineTo(x, canvas.height)
    //   ctx.stroke()
    // }
    // for {
    //   y <- 0 to canvas.height by blockSize
    // } yield {
    //   ctx.moveTo(0, y)
    //   ctx.lineTo(canvas.width, y)
    //   ctx.stroke()
    // }
    // ctx.strokeStyle = "rgb(0,0,0)"

    ws = Ws.newWebSocket()
    ws.onmessage = getBlock(ctx)
    ws.onclose = onConnectionClosed()

    ctx.lineWidth = 5;
    canvas.addEventListener("mousedown", (ev: MouseEvent) => onMouseDown(ev))
    canvas.addEventListener("mouseup", (ev: MouseEvent) => onMouseUp(ev))
    canvas.addEventListener(
      "mousemove",
      (ev: MouseEvent) => onMouseMove(canvas, ev),
      false
    )

    // clear button
    val clear =
      dom.document.getElementById("clear").asInstanceOf[HTMLButtonElement]
    clear.addEventListener(
      "click",
      (ev: MouseEvent) => onClickClear(canvas, ev)
    )
  }

  var mouseX: Double = 0
  var mouseY: Double = 0

  def onMouseDown(ev: MouseEvent): Unit = {
    startDrawClockTimer()
  }
  def onMouseUp(ev: MouseEvent): Unit = {
    stopDrawClockTimer()
  }
  def onMouseMove(canvas: HTMLCanvasElement, ev: MouseEvent): Unit = {
    val canvasRect = canvas.getBoundingClientRect()
    mouseX = ev.clientX - canvasRect.left
    mouseY = ev.clientY - canvasRect.top
  }
  def onClickClear(canvas: HTMLCanvasElement, ev: MouseEvent): Unit = {
    doClear(canvas)
    sendClear()
  }
  def doClear(canvas: HTMLCanvasElement): Unit = {
    ctx.fillStyle = "rgb(255,255,255)"
    ctx.fillRect(0, 0, canvas.width, canvas.height)
  }

  case class Ctx(
      ctx: CanvasRenderingContext2D,
      lastMouseX: Double,
      lastMouseY: Double,
      poisoningMap: collection.mutable.Map[(Int, Int), Boolean]
  )

  def xyToIndex(xory: Double): Int = {
    xory.toInt / blockSize
  }
  def lerp(x0: Double, y0: Double, x1: Double, y1: Double, x: Double) = {
    y0 + (y1 - y0) * (x - x0) / (x1 - x0)
  }
  def calcPoisoningMap(
      lx: Double,
      ly: Double,
      x: Double,
      y: Double
  ): Seq[(Int, Int)] = {
    // 線形補完
    val ln = xyToIndex(lx)
    val lm = xyToIndex(ly)
    val n = xyToIndex(x)
    val m = xyToIndex(y)

    val xs: Seq[Seq[(Int, Int)]] =
      for (x <- Math.min(ln, n) to Math.max(ln, n)) yield {
        val y = lerp(ln, lm, n, m, x).toInt
        Seq(
          (x - 1, y - 1),
          (x - 1, y),
          (x - 1, y + 1),
          (x, y - 1),
          (x, y),
          (x, y + 1),
          (x + 1, y - 1),
          (x + 1, y),
          (x + 1, y + 1)
        )
      }
    val ys: Seq[Seq[(Int, Int)]] =
      for (y <- Math.min(lm, m) to Math.max(lm, m)) yield {
        val x = lerp(lm, ln, m, n, y).toInt
        Seq(
          (x - 1, y - 1),
          (x - 1, y),
          (x - 1, y + 1),
          (x, y - 1),
          (x, y),
          (x, y + 1),
          (x + 1, y - 1),
          (x + 1, y),
          (x + 1, y + 1)
        )
      }
    xs.flatten ++ ys.flatten
  }

  var drawCtx: Ctx = null
  var drawClockTimerEnabled = false
  def startDrawClockTimer(): Unit = {
    drawClockTimerEnabled = true
    ctx.beginPath()
    drawCtx = Ctx(ctx, mouseX, mouseY, collection.mutable.Map())
    drawClockTimerTick()
    pushTimerTick()
  }
  def drawClockTimerTick(): Unit = {
    drawCtx.ctx.stroke()
    drawCtx.ctx.moveTo(drawCtx.lastMouseX, drawCtx.lastMouseY)
    drawCtx.ctx.lineTo(mouseX, mouseY)

    if (drawClockTimerEnabled) {
      for (
        nm <- calcPoisoningMap(
          drawCtx.lastMouseX,
          drawCtx.lastMouseY,
          mouseX,
          mouseY
        )
      ) {
        drawCtx.poisoningMap.update(nm, true)
      }
      drawCtx = drawCtx.copy(
        lastMouseX = mouseX,
        lastMouseY = mouseY
      )
      dom.window.setTimeout(() => drawClockTimerTick(), 10 /* millisec */ )
    }
  }
  def pushTimerTick(): Unit = {
    if (drawClockTimerEnabled) {
      if (drawCtx.poisoningMap.keys.size > 50) {
        pushBlocks(drawCtx)
        drawCtx = drawCtx.copy(poisoningMap = collection.mutable.Map())
      }
      dom.window.setTimeout(() => pushTimerTick(), 1000 /* millisec */ )
    }
  }
  def stopDrawClockTimer(): Unit = {
    drawClockTimerEnabled = false
    ctx.stroke()
    pushBlocks(drawCtx)
    drawCtx = drawCtx.copy(poisoningMap = collection.mutable.Map())
  }
  val b64encoder = java.util.Base64.getEncoder()
  var pushBuffer: Array[Byte] =
    Array.fill(4 * Protocol.blockSize * Protocol.blockSize)(255.toByte)
  def pushBlocks(ctx: Ctx): Unit = {
    for { nm <- ctx.poisoningMap.keys } yield {
      import io.circe.syntax._
      import Protocol._
      val (n, m) = nm
      pushBuffer = ctx.ctx
        .getImageData(n * blockSizeD, m * blockSizeD, blockSizeD, blockSizeD)
        .data
        .map(_.toByte)
        .toArray
      // debug output
      // ctx.ctx.rect(n* blockSizeD, m * blockSizeD, blockSizeD, blockSizeD)
      val msg = Protocol
        .PushBlock(n, m, b64encoder.encodeToString(pushBuffer))
        .asJson
        .noSpaces
      ws.send(msg.toString())
    }
  }

  def sendClear(): Unit = {
    import io.circe.syntax._
    import Protocol._
    val msg = Protocol.Clear().asJson.noSpaces
    ws.send(msg.toString())
  }

  var blockBuffer: ImageData = null

  val b64decoder = java.util.Base64.getDecoder()
  def getBlock(ctx: CanvasRenderingContext2D) = (msg: MessageEvent) => {
    import io.circe.parser._
    import Protocol._
    decode[PushBlock](msg.data.asInstanceOf[String]) match {
      case Right(pushBlock: PushBlock) => {
        val block = b64decoder.decode(pushBlock.block)
        val data = block.map(java.lang.Byte.toUnsignedInt)
        for (i <- 0 until blockBuffer.data.length) {
          blockBuffer.data(i) = data(i)
        }
        ctx.putImageData(
          blockBuffer,
          pushBlock.n * blockSizeD,
          pushBlock.m * blockSizeD,
          0,
          0,
          blockSizeD,
          blockSizeD
        )
      }
      case _ =>
    }

    decode[Clear](msg.data.asInstanceOf[String]) match {
      case Right(clear) => {
        doClear(canvas)
      }
    }
  }

  def onConnectionClosed() = (msg: CloseEvent) => {
    dom.window.alert("Connection closed unexpectedly. Please Reload.")
  }
}
