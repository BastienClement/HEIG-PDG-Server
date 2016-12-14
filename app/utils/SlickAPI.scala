package utils

import com.google.inject.Inject
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.ExecutionContext
import slick.driver.{JdbcProfile, MySQLDriver}
import slick.lifted.AppliedCompiledFunction

object SlickAPI extends MySQLDriver.API {
	@Inject private var dbc: DatabaseConfigProvider = _
	private lazy val DB = dbc.get[JdbcProfile].db

	@Inject private implicit var ec: ExecutionContext = _

	implicit class DBQueryExecutor[A](val q: Query[_, A, Seq]) extends AnyVal {
		@inline final def run = DB.run(q.result)
		@inline final def head = DB.run(q.result.head)
		@inline final def headOption = DB.run(q.result.headOption)
	}

	implicit class DBCompiledExecutor[A, B](val q: AppliedCompiledFunction[_, Query[A, B, Seq], _]) extends AnyVal {
		@inline final def run = DB.run(q.result)
		@inline final def head = DB.run(q.result.head)
		@inline final def headOption = DB.run(q.result.headOption)
	}

	implicit class DBRepExecutor[A](val q: Rep[A]) extends AnyVal {
		@inline final def run = DB.run(q.result)
	}

	implicit class DBIOActionExecutor[R](val q: DBIOAction[R, NoStream, Nothing]) extends AnyVal {
		@inline final def run = DB.run(q)
	}
}
