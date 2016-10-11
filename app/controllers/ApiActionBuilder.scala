package controllers

import com.google.inject.Provider
import models.{User, Users}
import play.api.Application
import play.api.http.Writeable
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import services.Crypto
import utils.DateTime
import utils.Implicits.futureWrapper
import utils.SlickAPI._

/**
  * Mixin trait for API controllers.
  * The controller must be injected with conf and an execution context.
  */
trait ApiActionBuilder extends Controller {
	val app: Provider[Application]

	implicit lazy val ec = app.get.injector.instanceOf[ExecutionContext]
	private lazy val crypto = app.get.injector.instanceOf[Crypto]

	/**
	  * Serializes Throwable instance into JSON objects.
	  * A null Throwable is serialized to JsNull.
	  */
	private[this] def serializeThrowable(t: Throwable, seen: Set[Throwable] = Set()): JsValue = {
		if (t == null || seen.contains(t)) JsNull
		else Json.obj(
			"class" -> t.getClass.getName,
			"message" -> t.getMessage,
			"trace" -> Try { t.getStackTrace.map(_.toString): JsValueWrapper }.getOrElse(Json.arr()),
			"cause" -> serializeThrowable(t, seen + t)
		)
	}

	implicit val throwableWrites: Writes[Throwable] = Writes[Throwable](serializeThrowable(_))

	/** Error message JS object */
	val writeSymbol: (Symbol => JsObject) = sym => Json.obj("err" -> sym.name)

	implicit val errorSymbol = Writeable[Symbol](
		writeSymbol.andThen(implicitly[Writeable[JsValue]].transform),
		Some("application/json")
	)

	implicit class WithCause(val sym: Symbol) {
		def withCause(t: Throwable): JsObject = writeSymbol(sym) + ("cause" -> serializeThrowable(t))
	}

	/** Safely invoke the action constructor and catch potential exceptions */
	private def wrap[R, A](block: R => Future[Result]): R => Future[Result] = (req: R) => {
		try {
			block(req).recover {
				case err: ApiException => err.status.apply(writeSymbol(err.sym))
				case err: Throwable => InternalServerError('UNCAUGHT_EXCEPTION.withCause(err))
			}
		} catch {
			case err: ApiException => err.status.apply(writeSymbol(err.sym))
			case err: Throwable => InternalServerError('UNCAUGHT_EXCEPTION.withCause(err))
		}
	}

	/** An API action */
	object ApiAction extends ActionBuilder[ApiRequest] {
		/** Returns the token from the Authorization header or query string parameter */
		private def token[A](implicit request: Request[A]): Option[String] = {
			request.getQueryString("token").orElse {
				request.headers.get("Authorization").collect {
					case auth if auth.toLowerCase.startsWith("token ") => auth.substring(6).trim
				}
			}.orElse {
				request.headers.get("X-Auth-Token")
			}
		}

		/** Decodes the token string and validates expiration date */
		private def decode(token: String): Option[JsObject] = {
			for {
				obj <- crypto.check(token)
				if (obj \ "expires").asOpt[DateTime].exists(_ > DateTime.now)
			} yield obj
		}

		/** Fetches the user from the request token, if available */
		private def user[A](implicit request: Request[A]): Future[Option[User]] = {
			token.flatMap(decode) match {
				case Some(tok) => Users.filter(_.id === (tok \ "user").as[Int]).headOption
				case None => Future.successful(None)
			}
		}

		/** Transforms a basic Request to ApiRequest */
		def transform[A](implicit request: Request[A]): Future[ApiRequest[A]] = {
			for (u <- user) yield ApiRequest(u.orNull, request)
		}

		/** Invoke the action's block */
		override def invokeBlock[A](request: Request[A], block: ApiRequest[A] => Future[Result]): Future[Result] = {
			transform(request).flatMap(wrap(block))
		}
	}

	/** An authenticated API action */
	object UserApiAction extends ActionBuilder[ApiRequest] {
		override def invokeBlock[A](request: Request[A], block: (ApiRequest[A]) => Future[Result]): Future[Result] = {
			ApiAction.transform(request).flatMap { req =>
				if (req.anon) Unauthorized('AUTHORIZATION_REQUIRED)
				else wrap(block)(req)
			}
		}
	}

	/** API exception */
	case class ApiException(sym: Symbol, status: Status = InternalServerError) extends Exception

	/** Accessor for queryString parameters */
	implicit class QueryStringReader(private val req: ApiRequest[_]) {
		private def map[T](key: String)(mapper: String => Option[T]): Option[T] = req.getQueryString(key).flatMap(mapper)
		def getQueryStringAsInt(key: String): Option[Int] = map(key) { s => Try(Integer.parseInt(s)).toOption }
	}

	implicit class SafeJsonAs(private val js: JsReadable) {
		def asSafe[T: Reads](e: ApiException): T = js.asOpt[T].getOrElse(throw e)
		def asSafe[T: Reads](sym: Symbol, status: Status = UnprocessableEntity): T = asSafe[T](ApiException(sym, status))
	}

	/** A placeholder for not implemented actions */
	def NotYetImplemented = Action { req => NotImplemented('NOT_YET_IMPLEMENTED) }
}
