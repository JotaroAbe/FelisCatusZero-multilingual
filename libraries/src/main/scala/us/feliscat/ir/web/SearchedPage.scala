package us.feliscat.ir.web

import java.io.IOException

import us.feliscat.converter.HtmlTextConverter
import org.jsoup.Connection.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import us.feliscat.text.{StringNone, StringOption}

/**
 * <pre>
 * Created on 5/29/15.
 * </pre>
 * @author K.Sakamoto
 */
class SearchedPage(val title: String,
                   val description: String,
                   val url: String,
                   val rank: Int) {
  private val negativeExtensionList: Seq[String] = Seq[String](
    "pdf",
    "ppt",
    "pptx"
  ) map {".".concat}

  def text: StringOption = {
    html match {
      case Some(document) =>
        HtmlTextConverter.toText(document)
      case None =>
        StringNone
    }
  }

  def html: Option[Document] = {
    negativeExtensionList foreach {
      case extension if url endsWith extension =>
        return None
      case _ =>
        //Do nothing
    }
    try {
      val response: Response = Jsoup.connect(url).timeout(10000).execute
      if (response.statusCode == 200 &&
          response.contentType == "us/feliscat/text/html") {
        Option(response.parse)
      } else {
        None
      }
    } catch {
      case e: IOException =>
        e.printStackTrace()
        None
    }
  }
}
