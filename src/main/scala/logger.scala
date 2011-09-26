package cronish

import java.util.logging.{Logger, Level}

object Logging {
  private val logger = Logger.getLogger(getClass.getName)
  
  def info(msg: String) = logger.info(msg)

  def warn(msg: String) = logger.warning(msg)

  def severe(msg: String) = logger.severe(msg)
}

