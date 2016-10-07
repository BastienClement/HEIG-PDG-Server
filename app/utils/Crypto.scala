package utils

import java.nio.charset.Charset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base64
import play.api.Configuration
import play.api.libs.json._
import scala.util.Try

/**
  * Crypto utilities
  */
object Crypto {
	/**
	  * Hashes a password with BCrypt.
	  *
	  * @param plaintext the plaintext password
	  * @return the hashed password
	  */
	def hash(plaintext: String) = BCrypt.hashpw(plaintext, BCrypt.gensalt())

	/**
	  * Checks a password against its hash.
	  *
	  * @param plaintext the plaintext password
	  * @param ref       the reference hashed password
	  * @return whether the password is valid or not
	  */
	def check(plaintext: String, ref: String) = BCrypt.checkpw(plaintext, ref)

	/**
	  * Normalize the JSON representation of an object prior to sign it.
	  *
	  * @param value a JSON value to normalize
	  * @return a normalized JSON encoding of the value
	  */
	private def normalize(value: JsValue): String = value match {
		case obj: JsObject =>
			"{" + obj.fields
			      .filter(p => !p._1.startsWith("$$"))
			      .map(p => p._1 + ":" + normalize(p._2))
			      .sorted
			      .mkString(",") + "}"
		case arr: JsArray =>
			"[" + arr.value.map(normalize).mkString(",") + "]"
		case other =>
			other.toString()
	}

	/**
	  * Computes the signature for a JSON value.
	  *
	  * @param obj  the JSON value to sign
	  * @param conf the server configuration object
	  * @return the signature string
	  */
	private def computeSignature(obj: JsValue)(implicit conf: Configuration): String = {
		val utf8 = Charset.forName("UTF-8")
		val hmac = Mac.getInstance("HmacSHA256")
		hmac.init(new SecretKeySpec(utf8.encode(conf.getString("token.key").get).array(), "HmacSHA256"))
		val data = hmac.doFinal(utf8.encode(normalize(obj)).array())
		data.map(b => Integer.toString((b & 0xff) + 0x100, 16).substring(1)).mkString
	}

	/**
	  * Signs the given JsObject.
	  *
	  * @param obj  the JSON object to sign
	  * @param conf the server configuration object
	  * @return an encoded token that can be sent to the client
	  */
	def sign(obj: JsObject)(implicit conf: Configuration): String = {
		val signature = JsString(computeSignature(obj))
		val signed = obj + ("$$sign" -> signature)
		Base64.encodeBase64String(signed.toString().getBytes("UTF-8"))
	}

	/**
	  * Decodes a token and check signature.
	  *
	  * @param token the client token
	  * @param conf  the server configuration object
	  * @return an option of the signed JSON value, if token is correct
	  */
	def check(token: String)(implicit conf: Configuration): Option[JsObject] = {
		val json = new String(Base64.decodeBase64(token), "UTF-8")
		val obj = Try {
			Json.parse(json).as[JsObject]
		}.map(Some(_)).getOrElse(None)
		obj.filter(o => (o \ "$$sign").asOpt[String].contains(computeSignature(o)))
	}
}
