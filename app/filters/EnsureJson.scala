package filters

import akka.stream.Materializer
import com.google.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Filter, RequestHeader, Result, Results}
import scala.concurrent.{ExecutionContext, Future}
import utils.Implicits.futureWrapper

@Singleton
class EnsureJson @Inject()(implicit override val mat: Materializer,
                           exec: ExecutionContext) extends Filter {
	private def wrapResponse(res: Future[Result]): Future[Result] = res.flatMap {
		case html if html.body.contentType.exists(_.startsWith("text/html")) =>
			html.body.consumeData.map { body =>
				Results.InternalServerError(Json.obj(
					"error" -> "HTML_RESULT_CONTENT_TYPE",
					"message" -> "The request response is an HTML document and has beed wrapped in a JSON object. Use ?html to disable this behavior or provide an 'Accept: text/html' header.",
					"status" -> html.header.status,
					"body" -> body.utf8String
				))
			}
		case other => other
	}

	override def apply(f: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
		if (rh.getQueryString("html").nonEmpty || rh.headers.get("Accept").exists(_.contains("html"))) f(rh)
		else wrapResponse(f(rh))
	}
}
