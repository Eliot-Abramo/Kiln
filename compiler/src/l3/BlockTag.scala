package l3

import scala.collection.mutable.Map as MutableMap

/**
  * Tags for blocks.
  *
  * @author Michel Schinz <Michel.Schinz@epfl.ch>
  */

//Maps symbolic block tag names to numeric tag codes

object BlockTag {
  val FreeBlock = 0x00
  val Function = 0x01
  val String = 0x02
  val RegisterFrame = 0xFF

  private val tagValue = MutableMap(
    "_free-block" -> FreeBlock,
    "_function" -> Function,
    "_string" -> String,
    "_register_frame" -> RegisterFrame
  )

  //what allows me to make new tags, if tag unknown, creates unique
  //8 bit address for tag
  def resolve(tagName: String): Int = {
    if (!tagValue.contains(tagName)) {
      val usedTags = tagValue.values.toSet
      tagValue.put(tagName, (0 to 0xFF).filterNot(usedTags).head)
    }
    tagValue(tagName)
  }
}
