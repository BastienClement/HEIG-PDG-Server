package gql

import models.User
import sangria.schema.{Args, Context, Field, ObjectType, OptionType, _}
import sangria.validation.ValueCoercionViolation
import scala.util.Try
import utils.Coordinates

trait Domain {
	this: GraphQL =>

	implicit def implicitContextUser(implicit context: Context[Ctx, _]): Option[User] = context.ctx.user

	def ConstantComplexity(complexity: Int): Option[(Ctx, Args, Double) => Double] = Some((c, a, d) => 5)

	case object CoordinatesCoercionViolation extends ValueCoercionViolation("Coordinates value expected")
	val CoordinatesType = ScalarType[Coordinates]("Coordinates",
		coerceOutput = (c, caps) => s"${c.lat},${c.lon}",
		coerceUserInput = {
			case s: String =>
				Try(Right(Coordinates.parse(s))).getOrElse(Left(CoordinatesCoercionViolation))
			case _ => Left(CoordinatesCoercionViolation)
		},
		coerceInput = {
			case sangria.ast.StringValue(s, _, _) =>
				Try(Right(Coordinates.parse(s))).getOrElse(Left(CoordinatesCoercionViolation))
			case _ => Left(CoordinatesCoercionViolation)
		})

	val UserType = ObjectType("User", fields[Ctx, User](
		Field("id", IntType, resolve = _.value.id),
		Field("username", StringType, resolve = _.value.username),
		Field("firstname", OptionType(StringType), resolve = implicit e => e.value.view.firstname),
		Field("lastname", OptionType(StringType), resolve = implicit e => e.value.view.lastname),
		Field("mail", OptionType(StringType), resolve = implicit e => e.value.view.mail),
		Field("rank", OptionType(IntType), resolve = implicit e => e.value.view.rank),
		Field("admin", BooleanType, resolve = _.value.admin),
		Field("location", OptionType(CoordinatesType), resolve = implicit env => ls.locationForUser(env.value.id),
			complexity = ConstantComplexity(5))
	))
}
