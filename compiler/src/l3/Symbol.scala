package l3

/**
  * A class for symbols, i.e. globally-unique names.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

/**
 * Compiler should distinguish two variables named x in different scopes,
 * even if their textual spelling is identical
 */

// Symbol stores:
//  name: String -> human readable base name
//  lazy unique id -> not evaluated until used
final class Symbol(val name: String, idProvider: => Int) {
  private lazy val id =
    idProvider

  //duplicates the symbol object while preserving name/id behavior source
  def copy(): Symbol =
    new Symbol(name, idProvider)

  override def toString: String =
    if (id == 0) name else s"${name}_${id}"
}

object Symbol {
  private val counters = scala.collection.mutable.HashMap[String,Int]()

  //generates a fresh symbol using a per-name counter
  def fresh(name: String): Symbol = {
    def id: Int = {
      val id = counters.getOrElse(name, 0)
      counters.put(name, id + 1)
      id
    }

    new Symbol(name, id)
  }
}
