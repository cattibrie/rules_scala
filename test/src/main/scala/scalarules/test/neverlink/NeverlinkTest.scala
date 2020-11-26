package neverlink

import org.scalatest._

class NeverlinkTest extends FlatSpec {
  "neverlink=False" should "include jar into classpath" in {
    getClass.getClassLoader.loadClass("neverlink.A")
  }

  "neverlink=True" should "exclude jar from classpath" in {
    assertThrows[ClassNotFoundException]{
      getClass.getClassLoader.loadClass("neverlink.B")
    }
  }
}