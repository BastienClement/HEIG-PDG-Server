import com.google.inject.{Inject, Provider, Singleton}
import controllers.api.ApiActionBuilder
import play.api.Application
import play.api.http.HttpErrorHandler
import play.api.mvc.{RequestHeader, Result}
import scala.concurrent.Future

@Singleton
class ErrorHandler @Inject() (val app: Provider[Application]) extends HttpErrorHandler with ApiActionBuilder {
	def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
		Future.successful(new Status(statusCode)('GENERIC_CLIENT_ERROR withDetails message))
	}

	def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
		Future.successful(InternalServerError('GENERIC_SERVER_ERROR withCause exception))
	}
}
