/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.play.bootstrap.filters

import java.util.Date

import akka.stream.Materializer
import javax.inject.Inject
import org.apache.commons.lang3.time.FastDateFormat
import org.joda.time.DateTimeUtils
import play.api.mvc.{Filter, RequestHeader, Result}
import play.api.routing.Router.Attrs
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.http.logging.LoggingDetails
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.config.ControllerConfigs
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

trait LoggingFilter extends Filter {
  private val dateFormat = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSSZZ")

  def controllerNeedsLogging(controllerName: String): Boolean

  val now: () => Long = DateTimeUtils.currentTimeMillis

  protected def logger: LoggerLike = Logger

  def apply(next: (RequestHeader) => Future[Result])(rh: RequestHeader): Future[Result] = {
    implicit val hc = HeaderCarrierConverter.fromHeadersAndSession(rh.headers)
    val startTime   = now()
    val result      = next(rh)

    if (needsLogging(rh)) {
      log(rh, result, startTime)
    } else {
      result
    }
  }

  private def needsLogging(request: RequestHeader): Boolean =
    request.attrs.get(Attrs.HandlerDef).forall { handlerDef =>
      controllerNeedsLogging(handlerDef.controller)
    }

  private def log(rh: RequestHeader, resultF: Future[Result], startTime: Long)(
    implicit ld: LoggingDetails): Future[Result] = {

    val start       = dateFormat.format(new Date(startTime))
    def elapsedTime = now() - startTime

    resultF
      .andThen {
        case Success(result) =>
          logger.info(
            s"${rh.method} ${rh.uri} ${result.header.status} ${elapsedTime}ms"
          )

        case Failure(NonFatal(t)) =>
          logger.info(
            s"${rh.method} ${rh.uri} $t ${elapsedTime}ms"
          )
      }
  }
}

class DefaultLoggingFilter @Inject()(config: ControllerConfigs)(implicit override val mat: Materializer)
    extends LoggingFilter {

  override def controllerNeedsLogging(controllerName: String): Boolean =
    config.get(controllerName).logging
}
