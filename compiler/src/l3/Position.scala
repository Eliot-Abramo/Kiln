package l3

/**
  * Tracks source locations for errors
  * Parser passes positions down contextually via 'using Position'
  * Name analysis and interpreters can then report precise diagnostics
  * 
  * Counts columns by character count, not unicode code-point count
  */

sealed trait Position

final class FilePosition(fileName: String, line: Int, column: Int)
    extends Position {
  override def toString: String = s"$fileName:$line:$column"
}

object UnknownPosition extends Position {
  override def toString: String = "<unknown position>"
}
