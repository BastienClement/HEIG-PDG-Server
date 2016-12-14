package controllers

import com.google.inject.{Inject, Provider}
import controllers.api.ApiActionBuilder
import models.{Event, Events}
import play.api.Application
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Controller
import scala.concurrent.Future
import scala.util.Try
import services.LocationService
import utils.SlickAPI._

class EventsController @Inject() (loc: LocationService)
                                 (val app: Provider[Application])
		extends Controller with ApiActionBuilder {

	def list = AuthApiAction.async { req =>
		Events.run.map { events =>
			Ok(events.map(Json.toJson[Event]))
		}
	}

	def get(id: Int) = AuthApiAction.async { req =>
		Events.findById(id).head.map { event => Ok(Json.toJson(event)) }.recover { case e => NotFound('EVENT_NOT_FOUND) }
	}

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
}
