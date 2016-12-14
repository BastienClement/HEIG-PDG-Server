package utils

import play.api.libs.json._

case class Coordinates(lat: Double, lon: Double)

object Coordinates {
	implicit val GeoJSONFormat = Json.format[Coordinates]

	def parse(str: String): Coordinates = {
		val Array(lat, lon) = str.split(",", 2).map(_.toDouble)
		Coordinates(lat, lon)
	}
}
