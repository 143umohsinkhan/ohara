package com.island.ohara.integration
import java.util
import java.util.concurrent.atomic.AtomicInteger

import com.island.ohara.io.CloseOnce
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.{BaseUser, WritePermission}

/**
  * a simple embedded ftp server providing 1 writable user. The home folder is based on java.io.tmpdir with prefix - ftp
  * 1) port -> a random port
  * 2) host -> "localhost"
  * 3) user -> a writable account
  * 4) password -> a writable account
  *
  * all resources will be released by FtpServer#close(). For example, all data in home folder will be deleted
  *
  * If ohara.it.ftp exists in env variables, local ftp server is not created.
  */
trait FtpServer extends CloseOnce {
  def host: String
  def port: Int
  def user: String
  def password: String
}

object FtpServer {
  private[integration] val FTP_SERVER: String = "ohara.it.ftp"

  private[this] val COUNT = new AtomicInteger(0)

  private[integration] def parseString(ftpString: String): (String, String, String, Int) = {
    // format => user:password@host:port
    try {
      val user = ftpString.split(":").head
      val password = ftpString.split("@").head.split(":").last
      val host = ftpString.split("@").last.split(":").head
      val port = ftpString.split("@").last.split(":").last.toInt
      (user, password, host, port)
    } catch {
      case e: Throwable => throw new IllegalArgumentException(s"invalid value of $FTP_SERVER", e)
    }
  }

  def apply(): FtpServer = if (sys.env.contains(FTP_SERVER)) {
    val (_user, _password, _host, _port) = parseString(sys.env(FTP_SERVER))
    new FtpServer {
      override def host: String = _host
      override def port: Int = _port
      override def user: String = _user
      override def password: String = _password
      override protected def doClose(): Unit = {}
    }
  } else {
    val _port = availablePort
    val homeFolder = createTempDir("ftp")
    val userManagerFactory = new PropertiesUserManagerFactory()
    val userManager = userManagerFactory.createUserManager
    val _user = new BaseUser()
    _user.setName(s"user-${COUNT.getAndIncrement()}")
    _user.setAuthorities(util.Arrays.asList(new WritePermission()))
    _user.setEnabled(true)
    _user.setPassword(s"password-${COUNT.getAndIncrement()}")
    _user.setHomeDirectory(homeFolder.getAbsolutePath)
    userManager.save(_user)

    val listenerFactory = new ListenerFactory()
    listenerFactory.setPort(_port)

    val factory = new FtpServerFactory()
    factory.setUserManager(userManager)
    factory.addListener("default", listenerFactory.createListener)

    val server = factory.createServer
    server.start()

    new FtpServer {
      override protected def doClose(): Unit = {
        server.stop()
        deleteFile(homeFolder)
      }

      override def host: String = "localhost"

      override def port: Int = _port

      override def user: String = _user.getName

      override def password: String = _user.getPassword
    }
  }
}
