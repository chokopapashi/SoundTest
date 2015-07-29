
import java.io.ByteArrayOutputStream

import javax.sound.sampled.{AudioFormat, AudioSystem, Line, LineUnavailableException, Port}
import javax.sound.sampled.{SourceDataLine, TargetDataLine}

import scala.util.control.Exception._

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor._
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy.Escalate
import akka.actor.SupervisorStrategy.Stop

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import org.hirosezouen.hzutil._
import HZLog._
import org.hirosezouen.hzactor._
import HZActor._

object SoundTest {
    implicit val logger = getLogger(this.getClass.getName)

    def getTargetDataLine(i1: Int, i2: Int): TargetDataLine = {
        val mi = AudioSystem.getMixerInfo()(i1)
        log_trace(s"MixerInfo:${mi.toString}")
        val m  = AudioSystem.getMixer(mi)
        log_trace(s"Mixer:${m.toString}")
        if(m.getTargetLineInfo.size == 0)
            throw new IllegalStateException()
        val tli = m.getTargetLineInfo()(i2)
        log_trace(s"TargetLineInfo:${tli.toString}")
        catching(classOf[Exception]) either {
            m.getLine(tli).asInstanceOf[TargetDataLine]
        } match {
            case Right(l) => {
                log_trace(s"Line:${l.toString}")
                l
            }
            case Left(th) => th match {
                case _: LineUnavailableException => throw th
                case _ => throw th
            }
        }
    }

    def getTargetDataLine(): TargetDataLine = {
        if(System.getProperty("os.name").startsWith("Windows"))
            getTargetDataLine(config.getInt("sound_test.mixer_index"), config.getInt("sound_test.target_line_index"))
        else if(System.getProperty("os.name").startsWith(""))
            getTargetDataLine(config.getInt("sound_test.mixer_index"), config.getInt("sound_test.target_line_index"))
        else
            throw new IllegalStateException()
    }

    def openLine(line: TargetDataLine) {
        catching(classOf[Exception]) either {
            line.open(new AudioFormat(8000, 8, 1, true, false))
        } match {
            case Right(_) => /* Nothing to do */
            case Left(th) => th match {
                case _: LineUnavailableException => throw th
                case _ => throw th
            }
        }
    }

    class LineInputActor(parent: ActorRef) extends Actor {
        log_trace("LineInputActor")

        case class ReceiveLoop()

        lazy val targetDataLine = getTargetDataLine()
        lazy val data = new Array[Byte](targetDataLine.getBufferSize/5)

        override def preStart() {

            val out = new ByteArrayOutputStream()
            var count: Int = 0

            openLine(targetDataLine)
            targetDataLine.start

            self ! ReceiveLoop()
        }

        def receive = {
            case HZStop() => {
                exitNormaly(parent)
            }
            case ReceiveLoop() => {
                val count = targetDataLine.read(data, 0, data.size)
                log_info("[" + data.take(count).map(b => f"$b%02X").mkString(",") + "]")
            }
            case x => log_debug(s"x=$x")
        }
    }
    object LineInputActor {
        def start(parent: ActorRef)(implicit system: ActorRefFactory): ActorRef =
            system.actorOf(Props(new LineInputActor(parent)), "LineInputActor")
    }

    class MainActor extends Actor {
        log_trace("MainActor")

        override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries=1, withinTimeRange=1 minutes, loggingEnabled=true) {
            case _: Exception => Stop
            case t => super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)
        }

        private var actorStates = HZActorStates()

        val quit_r = "(?i)^q$".r
        override def preStart() {
            actorStates += (
                InputActor.start(System.in) {
                    case quit_r() => System.in.close
                },
                LineInputActor.start(self)
            )
            log_trace(s"actorStates=$actorStates")
        }

        def receive = {
            case Terminated(actor) if(actorStates.contains(actor)) => {
                context.system.shutdown()
            }
            case x => log_debug(s"x=$x")
        }
    }
    object MainActor {
        def start(implicit system: ActorRefFactory): ActorRef =
            system.actorOf(Props(new MainActor), "MainActor")
    }

    lazy val config: Config = ConfigFactory.load

    def main(arg: Array[String]) {
        
        implicit val system = ActorSystem("SoundTest", ConfigFactory.load("akka_application.conf"))
        MainActor.start
        system.awaitTermination()
    }
}

