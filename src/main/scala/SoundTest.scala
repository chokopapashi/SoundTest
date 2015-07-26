
import javax.sound.sampled.{AudioFormat, AudioSystem, TargetDataLine}

import scala.util.control.Exception

object SoundTest {

  def setupDataLine(): TargetDataLine = {
    val mixerInfo = AudioSystem.getMixerInfo.find(find(_.toString.contains("Microphone")).get
    val format = new AudioFormat()
    val line: TargetDataLine
    val info: DataLine.Info = new DataLineInfo(classOf[TargetDataLine], format)

    if(!AudioSystem.isLineSupported(info)) {
      throw new IllegalArgumentException("unsupported DataLine ")
    }

    catching(classOf[Exception]) either {
      line = (AudioSystem.getLIne(info)).asInstanceOf[TargetDataLine]
      line.open(format)
    } match {
      case th: LineUnavailableException => throw th
      case th => throw th
    }
  }

  def main(arg: Array[String]) {
  }
}

