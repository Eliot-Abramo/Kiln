package l3

/**
  * A class for symbols, i.e. globally-unique names.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

final class Symbol(val name: String, idProvider: => Int) {
  private lazy val id =
    idProvider

  def copy(): Symbol =
    new Symbol(name, idProvider)

  override def toString: String =
    if (id == 0) name else s"${name}_${id}"
}

object Symbol {
  private val counters = scala.collection.mutable.HashMap[String,Int]()

  def fresh(name: String): Symbol = {
    def id: Int = {
      val id = counters.getOrElse(name, 0)
      counters.put(name, id + 1)
      id
    }

    new Symbol(name, id)
  }
}
