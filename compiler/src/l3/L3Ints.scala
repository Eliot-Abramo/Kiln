package l3

//Implement integer abstraction
object L3Ints {

  //L3Int locally shadowed as int, outside of this it's its own type
  opaque type L3Int = Int

  //clips to 31 bits by shifting left to discard top bit,
  //arithmetic shift right to restore sign
  private def ofIntClipped(v: Int): L3Int = L3Int((v << 1) >> 1)

  //all arithmetic and bitwise ops return clipped L3Int, that means
  //L3 integer arithmetic wraps/truncates to the 31 bit int model
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
    //31 bits because 2-complement signed
    val SIZE: Int = Integer.SIZE - 1

    def canConvertFromInt(i: Int): Boolean =
      fitsInNSignedBits(SIZE)(i)

    def canConvertFromIntUnsigned(i: Int): Boolean =
      fitsInNUnsignedBits(SIZE)(i)

    //Requires unsigned fit
    def ofIntUnsigned(v: Int): L3Int = {
      require(canConvertFromIntUnsigned(v))
      (v << 1) >> 1
    }

    //Requires signed 31 bit fit
    def apply(v: Int): L3Int = {
      require(canConvertFromInt(v))
      v
    }
  }
}
