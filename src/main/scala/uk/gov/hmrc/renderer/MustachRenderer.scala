/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.renderer

import java.util.concurrent.TimeUnit
import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import org.fusesource.scalate.{Template, TemplateEngine}
import play.twirl.api.Html
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.ws.WSGet
import scala.concurrent.Await
import scala.concurrent.duration._


trait TemplateRenderer {

  import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

  def connection: WSGet
  def templateServiceBaseUrl: String
  def refreshAfter: Duration

  val expireAfter: Duration = 60 minutes
  val maximumEntries: Int = 100

  protected val templateEngine = new TemplateEngine()

  private implicit val hc = HeaderCarrier()

  lazy val cache: LoadingCache[String, Template] =
    CacheBuilder.newBuilder()
      .maximumSize(maximumEntries)
      .refreshAfterWrite(refreshAfter.toMillis, TimeUnit.MILLISECONDS)
      .expireAfterWrite(expireAfter.toMillis, TimeUnit.MILLISECONDS)
      .build(new CacheLoader[String,Template] {
        override def load(path: String): Template =
          templateEngine.compileMoustache(Await.result[String](connection.GET(templateServiceBaseUrl + path).map(_.body), 10 seconds))
      })


  private def renderTemplate(path: String)(content: Html, extraArgs: Map[String, Any]): Html = {

    val attributes: Map[String, Any] = Map("article" -> content.body) ++ extraArgs
    val tpl: Template = cache.get(path)

    Html(templateEngine.layout("outPut.ssp", tpl, attributes))
  }

  def renderDefaultTemplate = renderTemplate("/template/mustache") _
}