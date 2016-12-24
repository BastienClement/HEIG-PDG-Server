package utils

import play.api.libs.json._
import scala.language.implicitConversions

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

	/** Implicitly converts from (Double, Double) to Coordinates */
	implicit def pack(coordinates: (Double, Double)): Coordinates = (Coordinates.apply _).tupled(coordinates)

	/** Implicitly converts from Coordinates to (Double, Double) */
	implicit def unpack(coordinates: Coordinates): (Double, Double) = (coordinates.lat, coordinates.lon)

	def parse(str: String): Coordinates = {
		val Array(lat, lon) = str.split(",", 2).map(_.toDouble)
		Coordinates(lat, lon)
	}
}
