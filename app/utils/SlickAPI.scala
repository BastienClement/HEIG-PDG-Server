package utils

import com.google.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.ExecutionContext
import slick.driver.{JdbcProfile, MySQLDriver}

object SlickAPI extends MySQLDriver.API {
	@Inject var dbc: DatabaseConfigProvider = _
	lazy val DB = dbc.get[JdbcProfile].db

	@Inject implicit var ec: ExecutionContext = _

	implicit class QueryExecutor[A](val q: Query[_, A, Seq]) extends AnyVal {
		@inline def run = DB.run(q.result)
		@inline def head = DB.run(q.result.head)
		@inline def headOption = DB.run(q.result.headOption)
	}

	implicit class DBIOActionExecutor[R](val q: DBIOAction[R, NoStream, Nothing]) extends AnyVal {
		@inline def run = DB.run(q)
	}
}
