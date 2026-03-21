/** No primitive data, everything is an object OOP oriented
  */

println("hello world")

/********************************************************************
 * Values
********************************************************************/
//you can name the results of expressions using val
//referencing a value doesn't re-compute it
val x = 1 + 1

//values can't be re-assigned
x = 3 //this won't work

//type of a value CAN be omitted and inferred
//or can be explicitely stated
val x: Int = 1 + 1

/********************************************************************
 * Variables
********************************************************************/
//Like values, except you can re-assign them
var x = 1 + 1
x = 3 //works because x is def as a var

//like values, type can be omitted and inferred or can be
//explicetly stated
var x: Int = 1 + 1

/********************************************************************
 * Blocks
********************************************************************/
//you can combine expressions by surrounding them with {}
//Result of the last expression in the block is the result
//of the overall block
println({
  val x = 1 + 1
  x + 1
}) //3

/********************************************************************
 * Functions
********************************************************************/
//expressions that have parameters and take arguments
//can def an anonymous function (i.e. function with no name)
//that returns a given integer plus 1:
(x: Int) => x + 1

// (list of param) => expression involving the parameters
// can also name
val addOne = (x: Int) => x + 1
println(addOne(1)) //2

val add = (x: Int, y: Int) => x + y
println(add(1, 2)) //3

//can also have no parameters
val getTheAnswer = () => 42
println(getTheAnswer()) //42

/********************************************************************
 * Methods
********************************************************************/
// look and behave very similar to functions
// defined with def, followed by name parameter list(s)
// return type and a body
def add(x: Int, y: Int): Int = x + y
println(add(1,2)) //3

//can take multiple parameter lists
def addThenMultiply(x: Int, y: Int)(multiplier: Int): Int = (x + y) * multiplier
println(addThenMultiply(1, 2)(3)) // 9

//or no parameter
def name: String = System.getProperty("user.name")
println("Hello " + name)

//can be multi-lined as well
def getSquareString(input: Double): String =
    val square = input*input
    square.toString
//last expression in the body is the method return value
//can do return using keyword, but rarely used
println(getSquareString(2.5))

/********************************************************************
  * Classes
********************************************************************/
//class keyword followed by its name and constructor parameters
class Greeter(prefix: String, suffix: String):
    def greet(name: String): Unit = 
        println(prefix+name+suffix)

//the return type of the method 'greet' is Unit -> nothing to return
//similar to void but everything in scala must return some value so 
//singleton value of type Unit written ()

val greeter = Greeter("Hello", "!")
greeter.greet("Scala developer")

/********************************************************************
  * Case Classes
********************************************************************/
//Immutable and they are compared by value unlike classes instances
//who are compared by reference
//useful for pattern matching
case class Point(x: Int, y:Int)

val point = Point(1, 2)
val anotherPoint = Point(1, 2)
val yetAnotherPoint = Point(2, 2)

//instances of case classes are compared by value not by reference
if point == anotherPoint then
  println(s"$point and $anotherPoint are the same.")
else
  println(s"$point and $anotherPoint are different.")
// ==> Point(1,2) and Point(1,2) are the same.

if point == yetAnotherPoint then
  println(s"$point and $yetAnotherPoint are the same.")
else
  println(s"$point and $yetAnotherPoint are different.")
// ==> Point(1,2) and Point(2,2) are different.


/********************************************************************
  * Objects
********************************************************************/
//single instances of their own definitions
//singeltons of their own classes
object IdFactory:
    private var counter=0
    def create(): Int =
        counter +=1
        counter

val newId: Int = IdFactory.create()
println(newId) //1
val newerId: Int = IdFactory.create()
println(newerId) //2

/********************************************************************
  * Traits
********************************************************************/
//abstract data types containing certain fields and methods
//in scala inheritance a class can only extend one other class,
//but can extend multiple traits
trait Greeter:
    def greet(name: String): Unit

//traits can have default implementations as well
trait Greeter:
    def greet(name: String): Unit
    println("hello" + name)

// you can extend traits with the extends keyword and override
//an implementation with override keyword
class DefaultGreeter extends Greeter

class CustomizableGreeter(prefix: String, postfix: String) extends Greeter:
    override def greet(name: String): Unit =
        println(prefix + name + postfix)

val greeter = DefaultGreeter()
greeter.greet("eliot")//hello eliot

val customGreeter = CustomizableGreeter("tkt", "aled")
customGreeter.greet("eliot") // tkt eliot aled

/********************************************************************
  * Program Entry point
********************************************************************/
//Main method is entry point
@main def hello() = println("aaaaaa")
