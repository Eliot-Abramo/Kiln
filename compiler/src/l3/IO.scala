package l3

/**
  * Helper module for IO functions in L₃ and intermediate languages.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

//runtime byte I/O helpers
object IO {
  def readByte(): Int =
    System.in.read()

  def writeByte(c: Int): Unit = {
    System.out.write(c)
    System.out.flush()
  }
}
