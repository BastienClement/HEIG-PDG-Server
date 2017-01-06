package utils

import play.api.libs.json.{JsObject, Reads}
import scala.concurrent.{ExecutionContext, Future}
import slick.dbio.Effect.Write
import slick.lifted.{FlatShapeLevel, Shape}
import utils.Patch.Mapping
import utils.SlickAPI.{Query, _}

/**
  * Description of a Patch operation.
  *
  * @param query    the row selection query
  * @param mappings the list of patch mappings to apply
  */
case class Patch[E, U, C[_]](query: Query[E, U, C], mappings: Vector[Mapping[E, U, C]] = Vector.empty) {
	private type FShape[F, T, G] = Shape[_ <: FlatShapeLevel, F, T, G]
	private type Self = Patch[E, U, C]

	/** Add a new mapping to this Patch operation */
	private def withMapping(mapping: Mapping[E, U, C]): Self = copy(mappings = mappings :+ mapping)

	/** Defines a generic mapping */
	def Map[F, G, T](extractor: JsObject => Option[T], fields: E => F)
	                (implicit shape: FShape[F, T, G]): Self = {
		withMapping(new Mapping[E, U, C] {
			def materialize(query: Query[E, U, C], row: U, patch: JsObject): Option[Action] = {
				extractor(patch).map { value =>
					query.map(fields).update(value)
				}
			}
		})
	}

	/** Defines a simple field mapping */
	def MapField[T: Reads, G](key: String, column: E => Rep[T])
	                         (implicit shape: FShape[Rep[T], T, G]): Self = {
		Map(doc => (doc \ key).asOpt[T], column)
	}

	/** Checks a precondition that must hold for the currently existing document */
	def Require(cond: U => Boolean, error: String = "requirement failed"): Self = {
		withMapping(new Mapping[E, U, C] {
			def materialize(query: Query[E, U, C], row: U, patch: JsObject): Option[Action] = {
				if (!cond(row)) throw new IllegalStateException(error)
				None
			}
		})
	}

	/** Execute the operation, using the provided patch document */
	def Execute(document: JsObject)(implicit ec: ExecutionContext): Future[U] = {
		(for {
			count <- query.length.result
			head <- query.result.headOption
		} yield (count, head)).flatMap {
			case (1, Some(row)) =>
				val actions = mappings.map(m => m.materialize(query, row, document)).collect {
					case Some(action) => action
				}
				DBIO.sequence(actions) andThen query.result.head
			case (0, _) =>
				DBIO.failed(new NoSuchElementException)
			case (n, _) =>
				DBIO.failed(new IllegalStateException(s"query matched $n rows"))
		}.transactionally.run
	}
}

object Patch {
	sealed trait Mapping[E, U, C[_]] {
		type Action = DBIOAction[Int, NoStream, Write]
		def materialize(query: Query[E, U, C], row: U, patch: JsObject): Option[Action]
	}

	def apply[E, U, C[_]](query: Query[E, U, C]): Patch[E, U, C] = new Patch(query)
}
