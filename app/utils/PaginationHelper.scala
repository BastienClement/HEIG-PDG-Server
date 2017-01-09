package utils

import controllers.api.ApiRequest
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Result, Results}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import utils.SlickAPI._

object PaginationHelper {
	/** Reads parameter from request */
	private def read(query: String, header: String, default: Int)(implicit req: ApiRequest[_]): Int = {
		Try(req.getQueryString(query).orElse(req.headers.get(header)).get.toInt).getOrElse(default)
	}

	/**
	  * Perform query pagination and output the items list.
	  *
	  * @param query the query to paginate
	  * @param req   the HTTP request
	  * @param ec    an instance of ExecutionContext
	  * @tparam E the table queried
	  * @tparam U the type of elements
	  * @return a future that will be resolved to a Json HTTP response listing the items
	  */
	def paginate[E, U: Writes](query: Query[E, U, Seq])
	                          (implicit req: ApiRequest[_], ec: ExecutionContext): Future[Result] = {
		val count = read("count", "X-Paginate-Count", 25) min 100 max 1
		val page = read("page", "X-Paginate-Page", 1) max 1

		val results = for {
			total <- query.length.result
			items <- query.drop((page - 1) * count).take(count).result.map(items => items.map(item => Json.toJson(item)))
		} yield (total, items)

		results.map { case (total, items) =>
			Results.Ok(Json.toJson(items)).withHeaders(
				"X-Paginate-Items" -> total.toString,
				"X-Paginate-Pages" -> (total.toDouble / count).ceil.toLong.toString
			)
		}.run
	}
}
