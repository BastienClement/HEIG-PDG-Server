package services

import com.google.inject.{Inject, Singleton}
import models.Event
import scala.concurrent.{ExecutionContext, Future}
import utils.Coordinates
import utils.SlickAPI._

@Singleton
class EventService @Inject() (implicit ec: ExecutionContext) {
	/**
	  * Searches nearby events.
	  *
	  * @param point  the point around which users are searched
	  * @param radius the search radius (in meters)
	  * @return a list of nearby events and their distance (in meters) from the given point
	  */
	def nearby(point: Coordinates, radius: Double): Future[Seq[(Event, Double)]] = {
		val (lat, lon) = Coordinates.unpack(point)
		sql"""
			SELECT *, earth_distance(ll_to_earth(${lat}, ${lon}), ll_to_earth(lat, lon)) AS dist
			FROM events
			WHERE earth_box(ll_to_earth(${lat}, ${lon}), ${radius}) @> ll_to_earth(lat, lon)
				AND earth_distance(ll_to_earth(${lat}, ${lon}), ll_to_earth(lat, lon)) <= ${radius}
			ORDER BY dist ASC""".as[(Event, Double)].run
	}
}
