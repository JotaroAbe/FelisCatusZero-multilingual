package modules.text.vector.wordembedding.fastText

import java.io.{File, IOException, PrintWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import modules.util.ModulesConfig
import us.feliscat.sentence.ja.JapaneseSentenceSplitter
import us.feliscat.text.analyzer.mor.mecab.UnidicMecab
import us.feliscat.text.{StringNone, StringOption, StringSome}

import scala.collection.mutable.ListBuffer
import scala.xml.{Elem, NodeSeq, Source, XML}

/**
  * <pre>
  * Created on 2016/12/26.
  * </pre>
  *
  * @author K.Sakamoto
  */
object TrainingDataGenerator {
  private val mEssayExamFiles = ListBuffer.empty[File]

  private def initialize(): Unit = {
    println(">> fastText Training - Initializing")
    ModulesConfig.essayExamDirOpt match {
      case Some(essayExamDir) =>
        val essayExamDirFile = new File(essayExamDir)
        if (essayExamDirFile.canRead && essayExamDirFile.isDirectory) {
          essayExamDirFile.listFiles foreach {
            case file: File if file.canRead && file.isFile && file.getName.endsWith(".xml") =>
              mEssayExamFiles += file
            case _ =>
            // Do nothing
          }
        }
      case None =>
      // Do nothing
    }
  }

  def main(args: Array[String]): Unit = {
    generate()
  }

  def generate(): Unit = {
    initialize()
    println(">> fastText Training - XML Parser Processing")
    val qt = ListBuffer.empty[QuestionInText]
    mEssayExamFiles.result foreach {
      file: File =>
        val xml: Elem = XML.load(Source.fromFile(file))
        xml \ "answer_section" foreach {
          answerSection: NodeSeq =>
            val builder = new StringBuilder()
            answerSection \ "instruction" \ "p" foreach {
              p: NodeSeq =>
                StringOption(p.text.trim) match {
                  case StringSome(line) =>
                    builder.
                      append(line).
                      append('\n')
                  case StringNone =>
                  //Do nothing
                }
            }
            val instruction: String = builder.result

            val buffer = ListBuffer.empty[String]
            answerSection \ "keyword_set" \ "keyword" foreach {
              keyword: NodeSeq =>
                StringOption(keyword.text.trim) match {
                  case StringSome(k) =>
                    buffer += k
                  case StringNone =>
                  //Do nothing
                }
            }
            val keywords: Seq[String] = buffer.result

            buffer.clear
            val answerSet: NodeSeq = answerSection \ "answer_set" \ "answer"
            val expressionSet: NodeSeq = answerSet \ "expression_set" \ "expression"
            expressionSet foreach {
              expression: NodeSeq =>
                if (java.lang.Boolean.parseBoolean((expression \ "@is_gold_standard").text.trim)) {
                  buffer += expression.text.trim
                }
            }
            val answers: Seq[String] = buffer.result

            val label: String = (answerSection \ "@label").text.trim
            val id: String = (answerSection \ "@id").text.trim

            qt += new QuestionInText(instruction, keywords, answers, file.getName, label, id)
        }

    }
    println(">> fastText Training - Morpheme Analyzer Processing")
    val qcwBuffer = ListBuffer.empty[QuestionInContentWord]
    qt.result foreach {
      q: QuestionInText =>
        var text: Seq[String] = Nil
        JapaneseSentenceSplitter.split(StringOption(q.text)) foreach {
          sentenceTmp =>
            text = UnidicMecab.extractContentWords(StringOption(sentenceTmp.text))
        }
        val answers = ListBuffer.empty[Seq[String]]
        q.answers foreach {
          answer: String =>
            val contentWordBuffer = ListBuffer.empty[String]
            JapaneseSentenceSplitter.split(StringOption(answer)) foreach {
              sentenceTmp =>
                val contentWordsInAnswer: Seq[String] = UnidicMecab.extractContentWords(StringOption(sentenceTmp.text))
                contentWordBuffer ++= contentWordsInAnswer
            }
            answers += contentWordBuffer.result
        }
        qcwBuffer += new QuestionInContentWord(q.keywords ++ text, answers.result, q.book, q.label, q.id)
    }
    qt.clear

    println(">> fastText Processing")
    var writerOpt = Option.empty[PrintWriter]
    try {
      val writer = new PrintWriter(
        Files.newBufferedWriter(Paths.get("out", "deep_learning", "training_data.txt"), StandardCharsets.UTF_8))
      writerOpt = Option(writer)
      qcwBuffer.result foreach {
        q: QuestionInContentWord =>
          val text: Seq[(String, Array[Float])] = FastTextVectorExtractor.extract(q.text)
          q.answers foreach {
            answer =>
              writer.print("Q ")
              writer.print(q.book)
              writer.print(" ")
              writer.print(q.label)
              writer.print(" ")
              writer.println(q.id)
              text foreach {
                wordVector =>
                  writer.print(wordVector._1)
                  writer.print(" ")
                  writer.println(wordVector._2.mkString(" "))
              }
              writer.println("A")
              FastTextVectorExtractor.extract(answer) foreach {
                wordVector =>
                  writer.print(wordVector._1)
                  writer.print(" ")
                  writer.println(wordVector._2.mkString(" "))
              }
          }
      }
    } catch {
      case e: IOException =>
        e.printStackTrace()
    } finally {
      if (writerOpt.nonEmpty) {
        writerOpt.get.close()
      }
    }
    qcwBuffer.clear
  }
  /*
      println(">> PrintWriter Processing")
      var writerOpt: Option[PrintWriter] = None
      try {
        val writer: PrintWriter = new PrintWriter(
          Files.newBufferedWriter(Paths.get("out", "deep_learning", "training_data.txt"), StandardCharsets.UTF_8))
        writerOpt = Option(writer)
        qwv.result foreach {
          q: QuestionInWordVector =>
            q.answers foreach {
              answer =>
                writer.print("Q ")
                writer.print(q.book)
                writer.print(" ")
                writer.print(q.label)
                writer.print(" ")
                writer.println(q.id)
                q.text foreach {
                  wordVector =>
                    writer.print(wordVector._1)
                    writer.print(" ")
                    writer.println(wordVector._2.mkString(" "))
                }
                writer.println("A")
                answer foreach {
                  wordVector =>
                    writer.print(wordVector._1)
                    writer.print(" ")
                    writer.println(wordVector._2.mkString(" "))
                }
            }
        }
      } catch {
        case e: IOException =>
          e.printStackTrace()
      } finally {
        if (writerOpt.nonEmpty) {
          writerOpt.get.close()
        }
      }
      */

  private class QuestionInText(val text: String, val keywords: Seq[String], val answers: Seq[String], val book: String, val label: String, val id: String)

  private class QuestionInContentWord(val text: Seq[String], val answers: Seq[Seq[String]], val book: String, val label: String, val id: String)

  //private class QuestionInWordVector(val text: Seq[(String, Array[Float])], val answers: Seq[Seq[(String, Array[Float])]], val book: String, val label: String, val id: String)
}