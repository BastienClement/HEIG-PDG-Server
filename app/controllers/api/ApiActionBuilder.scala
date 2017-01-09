package controllers.api

import com.google.inject.Provider
import models.{User, Users}
import play.api.Application
import play.api.cache.CacheApi
import play.api.http.Writeable
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.Try
import services.{CryptoService, FriendshipService}
import utils.Implicits.futureWrapper
import utils.SlickAPI._
import utils.{DateTime, ErrorStrings}

/**
  * Mixin trait for API controllers.
  *
  * This trait is a collection of utilities and implicits that are common to all
  * controller implementations in Eventail. It defined the basis of the error
  * handling mechanism and provide generic security checks for requests authentication.
  *
  * The actual controller instance should be injected with a Provider[Application],
  * allowing this trait to automatically pull in other dependencies without bloating
  * the injection list of the controller.
  */
trait ApiActionBuilder extends Controller {
	/** A provider of the Application instance */
	val app: Provider[Application]

	/** Pulls a dependency from the application injector */
	private def pull[T: ClassTag]: T = app.get.injector.instanceOf[T]

	/** The Play-provided execution context */
	implicit lazy val ec = pull[ExecutionContext]

	/** An instance of the crypto service */
	implicit lazy val crypto = pull[CryptoService]

	/** An instance of the cache service */
	implicit lazy val cache = pull[CacheApi]

	/** An instance of the friendship service */
	implicit lazy val fs = pull[FriendshipService]

	/**
	  * Serializes Throwable instance into JSON objects.
	  * A null Throwable is serialized to JsNull.
	  */
	private[this] def serializeThrowable(t: Throwable, seen: Set[Throwable] = Set()): JsValue = {
		if (t == null || seen.contains(t)) JsNull
		else Json.obj(
			"class" -> t.getClass.getName,
			"message" -> t.getMessage,
			"trace" -> Try {
				t.getStackTrace.map(_.toString): JsValueWrapper
			}.getOrElse(Json.arr()),
			"cause" -> serializeThrowable(t.getCause, seen + t)
		)
	}

	/** Writes instance for Throwables */
	implicit val throwableWrites: Writes[Throwable] = Writes[Throwable](serializeThrowable(_))

	/** Error message JS object */
	val writeSymbol: (Symbol => JsObject) = sym => Json.obj(
		"error" -> sym.name,
		"message" -> ErrorStrings.get(sym)
	)

	/** Writes instance for Symbols */
	implicit val errorSymbol: Writeable[Symbol] = Writeable[Symbol](
		writeSymbol.andThen(implicitly[Writeable[JsValue]].transform),
		Some("application/json")
	)

	implicit class WithOpsJsObject(val obj: JsObject) {
		def withCause(t: Throwable): JsObject = obj + ("cause" -> serializeThrowable(t))
		def withDetails[D](details: D)(implicit wjs: Writes[D]): JsObject = obj + ("details" -> wjs.writes(details))
	}

	implicit class WithOpsSymbol(val sym: Symbol) {
		def withCause(t: Throwable): JsObject = writeSymbol(sym) withCause t
		def withDetails[D: Writes](details: D): JsObject = writeSymbol(sym) withDetails details
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
		/** Parses the Authorization header */
		private def parseAuthorization(header: String): Option[String] = {
			if (header.toLowerCase.startsWith("token ")) Some(header.substring(6).trim)
			else None
		}

		/** Returns the token from the Authorization header or query string parameter */
		private def token[A](implicit request: Request[A]): Option[String] = {
			def queryString = request.getQueryString("token")
			def authorization = request.headers.get("Authorization").flatMap(parseAuthorization)
			def header = request.headers.get("X-Auth-Token")
			queryString orElse authorization orElse header
		}

		/** Decodes the token string and validates expiration date */
		private def decode(token: String): Option[JsObject] = {
			for {
				obj <- crypto.check(token)
				if (obj \ "expires").asOpt[DateTime].exists(_ > DateTime.now)
			} yield obj
		}

		/** Fetches the user from the request token, if available */
		private def user[A](token: Option[String])(implicit request: Request[A]): Future[Option[User]] = {
			token.flatMap(decode) match {
				case Some(tok) => Users.findById((tok \ "user").as[Int]).headOption
				case None => Future.successful(None)
			}
		}

		/** Transforms a basic Request to ApiRequest */
		def transform[A](implicit request: Request[A]): Future[ApiRequest[A]] = request match {
			case apiRequest: ApiRequest[A] => apiRequest
			case other =>
				val tok = token
				for (u <- user(tok)) yield ApiRequest(u.orNull, tok.orNull, other)
		}

		/** Invoke the action's block */
		override def invokeBlock[A](request: Request[A], block: ApiRequest[A] => Future[Result]): Future[Result] = {
			transform(request).flatMap(wrap(block))
		}
	}

	/** An authenticated API action */
	object AuthApiAction extends ActionBuilder[ApiRequest] {
		override def invokeBlock[A](request: Request[A], block: (ApiRequest[A]) => Future[Result]): Future[Result] = {
			ApiAction.transform(request).flatMap { req =>
				if (req.anon) Unauthorized('AUTHORIZATION_REQUIRED)
				else if (req.user.banned) throw ApiException('USER_BANNED, Forbidden)
				else wrap(block)(req)
			}
		}
	}

	/** Accessor for queryString parameters */
	implicit class QueryStringReaderOps(private val req: Request[_]) {
		def getQueryStringAs[T](key: String)(implicit qsrm: QueryStringReader[T]): Option[T] = {
			req.getQueryString(key).flatMap(qsrm.mapper)
		}

		def getQueryStringAsInt(key: String): Option[Int] = getQueryStringAs[Int](key)
		def getQueryStringAsDouble(key: String): Option[Double] = getQueryStringAs[Double](key)
	}

	/** Querystring parameter decoder */
	case class QueryStringReader[T](mapper: String => Option[T])

	/** Supports for basic types of input reading */
	object QueryStringReader {
		private def unsafeWrapper[T](mapper: String => T) = QueryStringReader[T](s => Try(mapper(s)).toOption)

		implicit val stringMapper: QueryStringReader[String] = QueryStringReader(Some.apply)
		implicit val intMapper: QueryStringReader[Int] = unsafeWrapper(_.toInt)
		implicit val doubleMapper: QueryStringReader[Double] = unsafeWrapper(_.toDouble)
	}

	/** Default value for missing parameters */
	protected def Unprocessable: Nothing = throw ApiException('UNPROCESSABLE_ENTITY, UnprocessableEntity)

	/**
	  * Attempts to read input parameters.
	  *
	  * This method will first attempt to read the value from the body payload, if this is a JSON object.
	  * If the first read fails, it will then attempt to read the parameter from the query string.
	  * Finally, if none of the reads were successful, the default value will be returned.
	  *
	  * By default, a missing parameter will throw the UNPROCESSABLE_ENTITY exception.
	  *
	  * @param name    the parameter name
	  * @param default the default value to return
	  * @param req     the request object
	  * @tparam T the type of the value to read
	  */
	def param[T: Reads : QueryStringReader](name: String, default: => T = Unprocessable)
	                                       (implicit req: Request[AnyContent]): T = {
		req.body.asJson.flatMap(b => (b \ name).asOpt[T]).orElse {
			req.getQueryStringAs[T](name)
		}.getOrElse(default)
	}

	/** A placeholder for not implemented actions */
	def NotYetImplemented = Action { req => NotImplemented('NOT_YET_IMPLEMENTED) }

	/** Implicitly construct a PointOfView as the user issuing the request. */
	protected implicit def implicitPointOfViewFromRequest(implicit req: ApiRequest[_]): Users.PointOfView = {
		new Users.PointOfView(req.user)
	}

	/**
	  * Provides simple operations on Future values.
	  *
	  * @param future the future value
	  * @tparam T the type of value of this future
	  */
	implicit class SimpleFutureOps[T](private val future: Future[T]) {
		/**
		  * Unconditionally replaces the content of this future by the provided value.
		  *
		  * @param value the replacement value
		  * @tparam U the type of the replacement value
		  * @return a new future that will be resolved to the replacement value
		  */
		def replace[U](value: => U): Future[U] = future.map(_ => value)

		/**
		  * Unconditionally recovers a failed future using the provided value.
		  *
		  * @param value the recovery value
		  * @tparam U the type of the recovery value
		  * @return a future that will be resolved to the given value if it were previously failed.
		  */
		def orElse[U >: T](value: => U): Future[U] = future.recover { case _ => value }
	}

	/**
	  * Automatically provides instances of Writable[A] if A can be converted to a JSON value.
	  *
	  * @param codec the Play codec instance
	  * @tparam A the type of value to serialize
	  * @return a Writable instance for the type A
	  */
	implicit def writableJson[A: Writes](implicit codec: Codec): Writeable[A] = {
		Writeable[A]((a: A) => codec.encode(Json.toJson(a).toString), Some("application/json"))
	}
}
