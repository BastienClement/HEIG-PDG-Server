package utils

import play.api.libs.json._

case class Coordinates(lat: Double, lon: Double)

object Coordinates {
	implicit val CoordinatesFormat: Format[Coordinates] = new Format[Coordinates] {
		def writes(coordinates: Coordinates): JsArray = Json.arr(coordinates.lat, coordinates.lon)
		def reads(json: JsValue): JsResult[Coordinates] = {
			for {
				lat <- json(0).validate[Double]
				lon <- json(1).validate[Double]
			} yield Coordinates(lat, lon)
		}
	}

	def parse(str: String): Coordinates = {
		val Array(lat, lon) = str.split(",", 2).map(_.toDouble)
		Coordinates(lat, lon)
	}
}
