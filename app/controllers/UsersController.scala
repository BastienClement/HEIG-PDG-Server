package controllers

import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.{ApiActionBuilder, ApiException, ApiRequest}
import play.api.Application
import play.api.mvc.Controller
import scala.util.Try
import utils.Implicits.{safeJsReadTyping, futureWrapper}

@Singleton
class UsersController @Inject() (val app: Provider[Application])
		extends Controller with ApiActionBuilder {

	private def userId[A](uid: String, selfOnly: Boolean = false)(implicit req: ApiRequest[A]): Int = uid match {
		case "self" => req.user.id
		case _ =>
			val id = Try(uid.toInt).getOrElse(throw ApiException('USERS_INVALID_UID, UnprocessableEntity))
			if (selfOnly && !req.userOpt.exists { u => u.id == id || u.admin }) {
				throw ApiException('USERS_SELF_ONLY, Forbidden)
			} else {
				id
			}
	}

	def list = NotYetImplemented

	def user(user: String) = ApiAction.async { req =>
		NotYetImplemented(req)
	}

	def location(user: String) = AuthApiAction.async(parse.json) { implicit req =>
		val uid = userId(user, selfOnly = true)
		val lat = (req.body \ "lat").to[Double]
		val lon = (req.body \ "lon").to[Double]

		Ok
	}
}
