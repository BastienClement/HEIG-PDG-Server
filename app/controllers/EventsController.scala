package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.{ApiActionBuilder, ApiException}
import models._
import play.api.Application
import play.api.libs.json.{JsNumber, JsObject, Json}
import play.api.mvc.{Controller, Result}
import scala.concurrent.Future
import services.EventService
import utils.DateTime.Units
import utils.SlickAPI._
import utils.{DateTime, PaginationHelper, PointOfView}

/**
  * The events controller.
  * This controller is responsible for everything related to events and points of interest.
  *
  * @param events an instance of the events service
  * @param app    a provider for the Play-application instance
  */
@Singleton
class EventsController @Inject() (events: EventService)
                                 (val app: Provider[Application])
		extends Controller with ApiActionBuilder {
	/**
	  * Ensures that an event is editable by the current user before executing the given action.
	  * An event is only editable by its owner or an administrator user.
	  *
	  * @param event  the event id
	  * @param action the action to perform if the event is editable
	  * @param pov    the point of view of the user issuing the request
	  * @tparam T the type of result returned by the action
	  * @return the result of the action, if allowed to take place
	  */
	private def ensureEventEditable[T <: Result](event: Int)
	                                            (action: => Future[T])
	                                            (implicit pov: PointOfView): Future[Result] = {
		events.canEditEvent(event).flatMap {
			case true => action
			case false => Future.successful(Forbidden('EVENT_EDIT_FORBIDDEN))
		}
	}

	/** Fetches the list of all events. */
	def list = AuthApiAction.async { implicit req =>
		if (!req.user.admin) throw ApiException('ADMIN_ACTION_RESTRICTED, Forbidden)
		PaginationHelper.paginate(Events.filter(_.end > DateTime.now))
	}

	/** Fetches information about a specific event. */
	def get(id: Int) = AuthApiAction.async { req =>
		Events.findById(id).head.map(event => Ok(Json.toJson(event))).recover {
			case _: NoSuchElementException => NotFound('EVENT_NOT_FOUND)
		}
	}

	/** Creates a new event. */
	def create = AuthApiAction.async(parse.tolerantJson) { req =>
		if (req.user.restricted) throw ApiException('USER_RESTRICTED, Forbidden)

		// Base JSON object for the event
		val body = req.body.as[JsObject]
		val base = Json.obj("id" -> 0, "owner" -> req.user.id) ++ body

		// Whether the event is spontaneous
		val spontaneous = (body \ "spontaneous").asOpt[Boolean].getOrElse(false)

		// Handle both cases
		val obj = if (spontaneous) {
			base ++ Json.obj(
				"spontaneous" -> true,
				"begin" -> DateTime.now,
				"end" -> (DateTime.now + 30.minutes)
			)
		} else {
			base ++ Json.obj(
				"spontaneous" -> false
			)
		}

		// Database insert
		(Events insert obj.as[Event]).run.map { ev =>
			Created(ev).withHeaders("Location" -> routes.EventsController.get(ev.id).url)
		}
	}

	/** Updates an event */
	def patch(id: Int) = AuthApiAction.async(parse.tolerantJson) { implicit req =>
		ensureEventEditable(id) {
			events.patch(id, req.body.as[JsObject])
					.map(ev => Ok(ev))
					.recover {
						case _: NoSuchElementException => NotFound('EVENT_NOT_FOUND)
					}
		}
	}

	/** Deletes an event */
	def delete(id: Int) = AuthApiAction.async { implicit req =>
		ensureEventEditable(id) {
			events.delete(id).map {
				case true => NoContent
				case false => NotFound('EVENT_NOT_FOUND)
			}
		}
	}

	/** List of events from the user. */
	def mine = AuthApiAction.async { implicit req =>
		PaginationHelper.paginate(Events.filter(e => e.end > DateTime.now && e.owner === req.user.id))
	}

	/** Searches for nearby events. */
	def nearby(lat: Double, lon: Double, radius: Double) = AuthApiAction.async {
		events.nearby((lat, lon), radius).map { events =>
			val combined = events.map { case (event, distance) =>
				Json.toJson(event).as[JsObject] + ("distance" -> JsNumber(distance))
			}
			Ok(Json.toJson(combined))
		}
	}

	/** Fetches the list of Point of Interest for a given event. */
	def listPOI(event: Int) = AuthApiAction.async {
		PointsOfInterest.findByEvent(event).run.map { poi =>
			Ok(Json.toJson(poi.map(Json.toJson(_))))
		}
	}

	/** Fetches a specific Point of Interest for a given event. */
	def getPOI(event: Int, id: Int) = AuthApiAction.async {
		events.getPOI(event, id).map(poi => Ok(poi)).recover {
			case _: NoSuchElementException => NotFound('EVENT_POI_NOT_FOUND)
		}
	}

	/** Creates a new Point of Interest for a given event. */
	def createPOI(event: Int) = AuthApiAction.async(parse.tolerantJson) { implicit req =>
		ensureEventEditable(event) {
			val poi = (req.body.as[JsObject] ++ Json.obj("event" -> event)).as[PointOfInterest]
			events.registerPOI(poi).map(poi => Ok(poi))
		}
	}

	/** Updates a specific Point of Interest for a given event. */
	def updatePOI(event: Int, id: Int) = AuthApiAction.async(parse.tolerantJson) { implicit req =>
		ensureEventEditable(event) {
			events.patchPOI(event, id, req.body.as[JsObject])
					.map(poi => Ok(poi))
					.recover {
						case _: NoSuchElementException => NotFound('EVENT_POI_NOT_FOUND)
					}
		}
	}

	/** Removes a Point of Interest from a given event. */
	def removePOI(event: Int, poi: Int) = AuthApiAction.async { implicit req =>
		ensureEventEditable(event) {
			events.removePOI(event, poi).map {
				case true => NoContent
				case false => NotFound('EVENT_POI_NOT_FOUND)
			}
		}
	}
}
