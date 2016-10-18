package services

import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.http.Writeable
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import scala.concurrent.Future

@Singleton
class ElasticSearch @Inject() (conf: Configuration, ws: WSClient) {
	val esBase = conf.getString("elasticsearch.base").getOrElse("http://127.0.0.1:9200")
	@inline final def apply(url: String): WSRequest = ws.url(s"$esBase$url")

	def get(url: String): Future[WSResponse] = apply(url).get()
	def delete[T: Writeable](url: String): Future[WSResponse] = apply(url).delete()
	def post[T: Writeable](url: String, body: T): Future[WSResponse] = apply(url).post(body)
	def put[T: Writeable](url: String, body: T): Future[WSResponse] = apply(url).put(body)
}
