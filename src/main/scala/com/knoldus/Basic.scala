package com.knoldus

import akka.Done
import java.io._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._
import spray.json.enrichAny
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.io.Source

object Basic extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val executionContext = system.dispatcher

  // fib method is a recursive method to calculate nth element in fibonacci series.
  def fib( num : Int) : Int = num match {
    case 0 | 1 => num
    case _ => fib( num-1 ) + fib( num-2 )
  }

  // fileCreate method creates a file and writes the content in it passed as arguments. 
  def fileCreate(name:String, content:String): Future[Done] = {
    val fileObject = new File(name)
    val printWriter = new PrintWriter(fileObject)
    printWriter.write(content)
    printWriter.close()
    Future { Done }
  }

  // fileGetter method reads the content of the given file. 
  def fileGetter(name:String): String = {
    val fileSource = Source.fromFile(name)
    val content = fileSource.mkString("")
    fileSource.close()
    content
  }

  // deleteFile method deletes the file passed as argument. 
  def deleteFile(name:String): Future[Done] = {
    val fileObject = new File(name)
    fileObject.delete()
    Future{Done}
  }

  //renameFile method renames the files with a new name given as argument.
  def renameFile(filename:String, newFileName: String): Future[Done] = {
    new File(filename).renameTo(new File(newFileName))
    Future{Done}
  }

  final case class FibObject (num: Int, result: Int)
  final case class Response (status: Boolean)
  final case class FileAccessor ( filename: String)
  final case class FileInfo (filename: String, fileContent: String)
  final case class FileRename (filename: String, newFileName: String)


  implicit val FibObjectFormat = jsonFormat2(FibObject)
  implicit val FileInfoFormat = jsonFormat2(FileInfo)
  implicit val FileRenameFormat = jsonFormat2(FileRename)
  implicit val ResponseFormat = jsonFormat1(Response)
  implicit val FileAccessorFormat = jsonFormat1(FileAccessor)

  val apiRoutes: Route = path("fibonacci"/IntNumber) { number => {
      val fibList = for {i <- List.range(1, number)}
        yield { fib(i) }

      val output = FibObject(number, fibList.last)
        get {
          complete (
            HttpEntity(
              ContentTypes.`application/json`,
              output.toJson.prettyPrint
            )
          )
        }
    }
  } ~ pathPrefix("file") {
    path("") {
      post {
        entity(as[FileInfo]) { file =>

          onSuccess(fileCreate(file.filename, file.fileContent)) { _ =>
            val res = Response(true)
            complete(
              HttpEntity(
                ContentTypes.`application/json`,
                res.toJson.prettyPrint
              )
            )
          }
        }
      }
    } ~ path("getFileContent"){
      post{
        entity(as[FileAccessor]){ name =>
          val readContent = fileGetter(name.filename)
          val output = FileInfo(name.filename, readContent)
          complete (
            HttpEntity(
              ContentTypes.`application/json`,
              output.toJson.prettyPrint
            )
          )
        }
      }
    } ~ path(Segment){ name =>
      delete{
        onSuccess(deleteFile(name)) { _ =>
          val res = Response(true)
          complete(
            HttpEntity(
              ContentTypes.`application/json`,
              res.toJson.prettyPrint
            )
          )
        }
      }
    } ~ path("rename"){
      put {
        entity(as[FileRename]) { name =>
          onSuccess(renameFile(name.filename,name.newFileName)) { _ =>
            val res = Response(true)
            complete(
              HttpEntity(
                ContentTypes.`application/json`,
                res.toJson.prettyPrint
              )
            )
          }
        }
      }
    }
  }

  Http().newServerAt("localhost", 8000).bind(apiRoutes)
  Await.result(system.whenTerminated, Duration.Inf)
}
