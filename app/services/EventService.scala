package services

import com.google.inject.{Inject, Singleton}
import models._
import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext, Future}
import utils.SlickAPI._
import utils.{Coordinates, Patch}

@Singleton
class EventService @Inject() (implicit ec: ExecutionContext) {
	/**
	  * Checks whether the user from a specific point of view can edit an event.
	  * An administrator user can always edit an event.
	  *
	  * @param event the event being edited
	  * @param pov   the user's point of view
	  */
	def canEditEvent(event: Int)(implicit pov: Users.PointOfView): Future[Boolean] = {
		if (pov.admin) Future.successful(true)
		else Events.findById(event).filter(e => e.owner === pov.user.id).exists.run
	}

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

	/**
	  * Fetches a specific Point of Interest.
	  *
	  * @param id the POI id
	  */
	def getPOI(event: Int, id: Int): Future[Option[PointOfInterest]] = PointsOfInterest.findByKey(event, id).headOption

	/**
	  * Registers a new Point of Interest in the database.
	  * The actual `id` field of the inserted object is ignored.
	  *
	  * @param poi the POI to register
	  * @return the complete POI object, with a valid `id` field value
	  */
	def registerPOI(poi: PointOfInterest): Future[PointOfInterest] = {
		val insert = PointsOfInterest returning PointsOfInterest.map(_.id) into ((poi, id) => poi.copy(id = id))
		(insert += poi).run
	}

	/**
	  * Patches a Point of Interest.
	  *
	  * @param event the event id of this POI
	  * @param id    the id of this POI
	  * @param patch the patch document
	  * @return a future that will be resolved with the updated point of interest
	  */
	def patchPOI(event: Int, id: Int, patch: JsObject): Future[PointOfInterest] = {
		Patch(PointsOfInterest.findByKey(event, id))
				.MapField("title", _.title)
				.MapField("desc", _.desc)
				.Map(doc => (doc \ "location").asOpt[Coordinates].map(Coordinates.unpack), poi => (poi.lat, poi.lon))
				.Execute(patch)
	}

	/**
	  * Removes a Point of Interest from a given event.
	  *
	  * @param event the event id from which the point of interest should be removed
	  * @param id    the point of interest id
	  * @return a future that will resolve to true if the POI was deleted, to false otherwise
	  */
	def removePOI(event: Int, id: Int): Future[Boolean] = {
		PointsOfInterest.filter(poi => poi.event === event && poi.id === id).delete.run.map {
			case 0 => false
			case 1 => true
		}
	}
}
