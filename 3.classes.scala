/********************************************************************
  * Defining a class
********************************************************************/
class user
val user1 = user()

//user has a default constructor which takes no arguments because no
//constructor was defined. But usually more like:
class Point(var x: Int, var y: Int):

  def move(dx: Int, dy: Int): Unit =
    x = x + dx
    y = y + dy

  override def toString: String =
    s"($x, $y)"
end Point

val point1 = Point(2, 3)
println(point1.x)  // prints 2
println(point1)    // prints (2, 3)

//The primary constructor is in the class signature (var x: Int, var y: Int)
//The move method takes 2 int and returns a unit value ()

/********************************************************************
  * Constructors
********************************************************************/
//constructors have optional parameters by providing a default value:
class Point(var x: Int=0, var y: Int=0)
val origin = Point()
val point1 = Point(1)//x=1 y=0
//or
val point1 = Point(x=1)//x=1 y=0

println(point1) //(1, 0)

/********************************************************************
  * Private Members and getter/setter
********************************************************************/
//by default public, use private to hide them
//setters have special _= appended to the identifier followed by the parameters
class Point:
  private var _x = 0
  private var _y = 0
  private val bound = 100

  //getter
  def x: Int = _x
  //setter
  def x_=(newValue: Int): Unit =
    if newValue < bound then
      _x = newValue
    else
      printWarning()

  def y: Int = _y
  def y_=(newValue: Int): Unit =
    if newValue < bound then
      _y = newValue
    else
      printWarning()

  private def printWarning(): Unit =
    println("WARNING: Out of bounds")
end Point

val point1 = Point()
point1.x = 99
point1.y = 101 // prints the warning

//primary constructor of val and var are public but val are immutable so
//this doesn't work:
class Point(val x:Int, val y:Int)
val point = Point(1,2)
point.x =3 //nope

//parameters without val or var are private values visible only in class
class Point(x:Int, y:Int)
val point = Point(1,2)
point.x =3 //nope

//can ommit param values by using default values
def log(message: String, level: String = "INFO") = println(s"$level: $message")

log("System starting")  // prints INFO: System starting
log("User not found", "WARNING")  // prints WARNING: User not found

//when calling scala from java code, need to have default parameters

//Scala doesn't allow having 2 methods with default parameters and with 
//the same name
object A {
  def func(x: Int = 34): Unit
  def func(y: String = "abc"): Unit
}
//nope won't work, who do I call?

/********************************************************************
  * Named Arguments
********************************************************************/
//when calling methods, you can label the arguments with their parameter names:
def printName(first: String, last: String): Unit =
  println(s"$first $last")
printName("John", "Public")  // Prints "John Public"
printName(first = "John", last = "Public")  // Prints "John Public"
printName(last = "Public", first = "John")  // Prints "John Public"
printName("Elton", last = "John")  // Prints "Elton John"

// named arguments can be called in any order but if out of order,
//need to name all of the parameters