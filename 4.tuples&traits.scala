/********************************************************************
  * Traits
********************************************************************/
//used to share interfaces and fields between classes
//classes and objects can extend traits, but traits can't be instantiated
//and therefore have no parameters
trait HarColor

trait Iterator[A]:
  def hasNext: Boolean
  def next(): A

//extending Iterator[A] requires a type A and implementations of the methods
//hasNext and next

class IntIterator(to: Int) extends Iterator[Int]:
  private var current =0
  override def hasNext: Boolean = current < to
  override def next(): Int = 
    if hasNext then
      val t=current
      current +=1
      t
    else
      0
end IntIterator

val iterator = IntIterator(10)
iterator.next()//0
iterator.next()//1

/********************************************************************
  * Subtyping
********************************************************************/
//where a given trait is required, a subtype of the trait can be used instead
import scala.collection.mutable.ArrayBuffer
trait Pet:
  val name: String

class Cat(val name:String) extends Pet
class Dog(val name:String) extends Pet

val dog = Dog("A")
val cat = Cat("B")

val animals = ArrayBuffer.empty[Pet]
animals.append(dog)
animals.append(cat)
animals.foreach(pet => println(pet.name))  // Prints A B

//the trait Pet has an abstract field name that gets implemented by 
//dog and cat in their constructors. On the last line we call pet.name
//which must be implemented in any subtype of the trait Pet

/********************************************************************
  * Tuples
********************************************************************/
//a value that contains a fixed number of elements, each with its own type
//immutable
val ingredient = ("Sugar", 25)
//inferred type of ingredient is (String, int)

//to access:
println(ingredient(0)) // Sugar
println(ingredient(1)) // 25

/********************************************************************
  * Pattern matching on tuples
********************************************************************/
val (name, quantity) = ingredient
println(name)     // Sugar
println(quantity) // 25


val planets =
  List(("Mercury", 57.9), ("Venus", 108.2), ("Earth", 149.6),
       ("Mars", 227.9), ("Jupiter", 778.3))

planets.foreach {
  case ("Earth", distance) =>
    println(s"Our planet is $distance million kilometers from the sun")
  case _ =>
}


val numPairs = List((2, 5), (3, -7), (20, 56))
for (a, b) <- numPairs do
  println(a * b)

/********************************************************************
  * Tuples vs Case classes
********************************************************************/
//case classes have named elements, these names can improve readability
