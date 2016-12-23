package utils

import play.api.libs.json._

case class Coordinates(lat: Double, lng: Double)

object Coordinates {
	implicit val CoordinatesFormat: Format[Coordinates] = new Format[Coordinates] {
		def writes(coordinates: Coordinates): JsArray = Json.arr(coordinates.lat, coordinates.lng)
		def reads(json: JsValue): JsResult[Coordinates] = {
			for {
				lat <- json(0).validate[Double]
				lng <- json(1).validate[Double]
			} yield Coordinates(lat, lng)
		}
	}

	def parse(str: String): Coordinates = {
		val Array(lat, lon) = str.split(",", 2).map(_.toDouble)
		Coordinates(lat, lon)
	}
}
