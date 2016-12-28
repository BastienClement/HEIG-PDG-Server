package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.ApiActionBuilder
import models._
import play.api.Application
import play.api.libs.json.{JsNumber, JsObject, Json}
import play.api.mvc.{Controller, Result}
import scala.concurrent.Future
import scala.util.Try
import services.EventService
import utils.SlickAPI._

@Singleton
class EventsController @Inject() (events: EventService)
                                 (val app: Provider[Application])
		extends Controller with ApiActionBuilder {

	/** Ensures that an event is editable by the current user before executing the given action. */
	private def ensureEventEditable[T <: Result](event: Int)
	                                            (action: => Future[T])
	                                            (implicit pov: Users.PointOfView): Future[Result] = {
		events.canEditEvent(event).flatMap {
			case true => action
			case false => Future.successful(Forbidden('EVENT_EDIT_FORBIDDEN))
		}
	}

	/** Fetches the list of all events. */
	def list = AuthApiAction.async { req =>
		Events.run.map { events =>
			Ok(events.map(Json.toJson[Event]))
		}
	}

	/** Fetches information about a specific event. */
	def get(id: Int) = AuthApiAction.async { req =>
		Events.findById(id).head.map(event => Ok(Json.toJson(event))).orElse(NotFound('EVENT_NOT_FOUND))
	}

	/** Creates a new event. */
	def create = AuthApiAction.async(parse.tolerantJson) { req =>
		Try {
			(req.body.as[JsObject] ++ Json.obj("id" -> 0, "owner" -> req.user.id)).as[Event]
		}.map { event =>
			(Events.returning(Events.map(_.id)) += event).run.map { id =>
				Created(event.copy(id = id)).withHeaders("Location" -> routes.EventsController.get(id).url)
			}.recover { case e =>
				InternalServerError('EVENT_CREATE_ERROR withCause e)
			}
		}.recover { case e =>
			Future.successful(BadRequest('EVENT_BAD_REQUEST withCause e))
		}.get
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
		events.getPOI(event, id).map {
			case Some(poi) => Ok(poi)
			case None => NotFound('EVENT_POI_NOT_FOUND)
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
			val poi = (req.body.as[JsObject] ++ Json.obj("event" -> event, "id" -> id)).as[PointOfInterest]
			events.updatePOI(poi).map {
				case true => NoContent
				case false => NotFound('EVENT_POI_NOT_FOUND)
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
