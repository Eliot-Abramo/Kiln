/********************************************************************
  * Type hierarchy
********************************************************************/
//any is the supertype of all types
//      two subclasses: AnyVal and AnyRef

//AnyVal represents value types, 9 value types and they are non-nullable:
//      double, int, char, bool, long, float, short, byte, unit

//AnyRef represents reference types, all non-value types are reference types
//      List, option, YourClass
//           underlying type is Null

//Underlying type of everything is Nothing

val list: List[Any] = List(
    "a atring",
    732,
    'c',
    true,
    () => "an anonymous function returning a string"
)
list.foreach(element => println(element))

/********************************************************************
  * Type Casting
********************************************************************/
// Byte --> Short --> Int --> Long --> Float --> Double
//               Char--^
val x: Long = 987654321
val y: Float = x.toFloat  // 9.8765434E8 (note that some precision is lost in this case)

val face: Char = '☺'
val number: Int = face  // 9786

//Casting is unidirectional, this won't compile:
val x: Long = 987654321
val y: Float = x.toFloat
val z: Long = y //nope, won't work
