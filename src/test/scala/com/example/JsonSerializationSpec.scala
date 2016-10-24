package com.example

import org.scalatest._
import spray.json._

class JsonSerializationSpec extends FlatSpec with Matchers with JsonSupport {

  "Put" should "serialize json" in {
    val json = """{ "userId": "1", "amount": 150 }"""
    val put = Put("1",150)
    put should equal(JsonParser(json).convertTo[Put])
  }

  "Transfer" should "serialize json" in {
    val json = """{ "senderId": "1", "recipientId": "2", "amount": 150 }"""
    val transfer = Transfer("1","2",150)
    transfer should equal(JsonParser(json).convertTo[Transfer])
  }
}
