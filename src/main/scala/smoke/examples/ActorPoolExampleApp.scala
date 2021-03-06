package smoke.examples

import smoke._

import akka.actor._
import akka.routing.RoundRobinRouter
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

object NotFoundException extends Exception("Not found")

class PooledResponder extends Actor {
  def receive = {
    case GET(Path("/example")) ⇒
      Thread.sleep(1000)
      sender ! Response(Ok, body = "It took me a second to build this response.\n")
    case _ ⇒ sender ! Status.Failure(NotFoundException)
  }
}

object ActorPoolExampleApp extends SmokeApp {
  val config = ConfigFactory.load().getConfig("smoke")
  val system = ActorSystem("ActorPoolExampleApp", config)
  val executionContext = system.dispatcher
  val pool = system.actorOf(Props[PooledResponder].withRouter(RoundRobinRouter(200)))
  implicit val timeout = Timeout(10000)

  onRequest(pool ? _ mapTo manifest[Response])

  onError {
    case NotFoundException ⇒ Response(NotFound)
    case e: Exception      ⇒ Response(InternalServerError, body = e.getMessage)
  }

  after { response ⇒
    val headers = response.headers :+ ("Server", "ActorPoolExampleApp/0.0.1")
    Response(response.status, headers, response.body)
  }

  beforeShutdown {
    println("Shutdown in 5 s")
    Thread.sleep(5000)
  }

  afterShutdown {
    println("Shutdown complete!")
  }
}

