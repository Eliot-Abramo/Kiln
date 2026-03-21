package l3

object L3Ints {

  opaque type L3Int = Int

  private def ofIntClipped(v: Int): L3Int = L3Int((v << 1) >> 1)

  extension (x: L3Int) {
    def toInt: Int = x

    def +(y: L3Int): L3Int = ofIntClipped(x + y)
    def -(y: L3Int): L3Int = ofIntClipped(x - y)
    def *(y: L3Int): L3Int = ofIntClipped(x * y)
    def /(y: L3Int): L3Int = ofIntClipped(x / y)
    def %(y: L3Int): L3Int = ofIntClipped(x % y)
    def &(y: L3Int): L3Int = ofIntClipped(x & y)
    def |(y: L3Int): L3Int = ofIntClipped(x | y)
    def ^(y: L3Int): L3Int = ofIntClipped(x ^ y)
    def <<(y: L3Int): L3Int = ofIntClipped(x << y)
    def >>(y: L3Int): L3Int = ofIntClipped(x >> y)

    def <(y: L3Int): Boolean = x < y
    def <=(y: L3Int): Boolean = x <= y
    def >(y: L3Int): Boolean = x > y
    def >=(y: L3Int): Boolean = x >= y

    def toString: String = x.toString
  }

  object L3Int {
    val SIZE: Int = Integer.SIZE - 1

    def canConvertFromInt(i: Int): Boolean =
      fitsInNSignedBits(SIZE)(i)

    def canConvertFromIntUnsigned(i: Int): Boolean =
      fitsInNUnsignedBits(SIZE)(i)

    def ofIntUnsigned(v: Int): L3Int = {
      require(canConvertFromIntUnsigned(v))
      (v << 1) >> 1
    }

    def apply(v: Int): L3Int = {
      require(canConvertFromInt(v))
      v
    }
  }
}
