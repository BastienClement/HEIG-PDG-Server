package controllers.api

import play.api.mvc.Results

/**
  * An exception occurring during the processing of an API request.
  *
  * An uncaught instance of this exception does not generate a 'UNCAUGHT_EXCEPTION error,
  * but uses the symbol and the status code from the exception instance instead.
  *
  * This is the only kind of exception that should be thrown from an API endpoint implementation.
  *
  * @param sym    the error symbol
  * @param status the status code to use
  */
case class ApiException(sym: Symbol, status: Results#Status = Results.InternalServerError,
                        cause: Option[Throwable] = None, details: Option[String] = None) extends Exception
