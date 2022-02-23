package steward.merge

import zio.ZIO

object Utilities {
  def mapN[R, E, A, B, C, D, F, G](zio1: ZIO[R, E, A], zio2: ZIO[R, E, B], zio3: ZIO[R, E, C], zio4: ZIO[R, E, D], zio5: ZIO[R, E, F])(
    f: (A, B, C, D, F) => G
  ): ZIO[R, E, G] =
    for {
      a <- zio1
      b <- zio2
      c <- zio3
      d <- zio4
      e <- zio5
    } yield f(a, b, c, d, e)
}
