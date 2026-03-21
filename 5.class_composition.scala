/********************************************************************
  * Class Composition with mixins
********************************************************************/
//mixins are traits which are used to compose a class
abstract class A:
    val message: String
class B extends A:
    val message = "I m an instance of B"
trait C extends A:
  def loudMessage = message.toUpperCase()
class D extends B, C

val d = D()
println(d.message)  // I'm an instance of class B
println(d.loudMessage)  // I'M AN INSTANCE OF CLASS B

//a class can extend only one class but as many else as it wants,
//can have same supertype

//class has an abstract type T and the standard iterator methods
abstract class AbsIterator:
    type T
    def hasNext: Boolean
    def next(): T

class StringIterator(s: String) extends AbsIterator:
    type T = Char
    private var i =0
    def hasNext = i<s.length()
    def next()=
        val ch = s charAt i
        i+=1
        ch
//because it is a trait, doesn't need to implement the abstract members of
//AbsIterator
trait RichIterator extends AbsIterator:
  def foreach(f: T => Unit): Unit = while hasNext do f(next())

//StringIterator as superclass and RichIterator as mixin
class RichStringIter extends StringIterator("Scala"), RichIterator
val richStringIter = RichStringIter()
richStringIter.foreach(println)

/********************************************************************
  * Case Classes
********************************************************************/
/********************************************************************
  * Case Classes
********************************************************************/
/********************************************************************
  * Case Classes
********************************************************************/
/********************************************************************
  * Case Classes
********************************************************************/