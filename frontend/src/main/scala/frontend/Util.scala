package frontend

object Util {
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

  def lerp(x0: Double, y0: Double, x1: Double, y1: Double, x: Double) = {
    y0 + (y1 - y0) * (x - x0) / (x1 - x0)
  }

  def xyToIndex(xory: Double): Int = {
    xory.toInt / shared.Protocol.blockSize
  }
}
