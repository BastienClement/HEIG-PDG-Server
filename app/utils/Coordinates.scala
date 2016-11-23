package utils

import play.api.libs.json._

case class Coordinates(lat: Double, lon: Double)

object Coordinates {
	implicit val GeoJSONFormat = new Format[Coordinates] {
		def reads(json: JsValue): JsResult[Coordinates] = {
			for {
				lon <- json(0).validate[Double]
				lat <- json(1).validate[Double]
			} yield Coordinates(lat, lon)
		}
		def writes(coords: Coordinates): JsValue = Json.arr(coords.lon, coords.lat)
	}

	def parse(str: String): Coordinates = {
		val Array(lat, lon) = str.split(",", 2).map(_.toDouble)
		Coordinates(lat, lon)
	}
}
