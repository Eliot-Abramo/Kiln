package l3

sealed trait Position

final class FilePosition(fileName: String, line: Int, column: Int)
    extends Position {
  override def toString: String = s"$fileName:$line:$column"
}

object UnknownPosition extends Position {
  override def toString: String = "<unknown position>"
}
