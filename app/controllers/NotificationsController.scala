package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.ApiActionBuilder
import play.api.Application
import play.api.mvc.Controller
import services.NotificationsService

@Singleton
class NotificationsController @Inject() (ns: NotificationsService)
                                        (val app: Provider[Application])
		extends Controller with ApiActionBuilder {
	def notifications = AuthApiAction.async { implicit req =>
		ns.fetch(req.user.id, req.token).map(list => Ok(list)).orElse(Conflict)
	}
}
