package com.perpcorp.edgellm

import com.perpcorp.edgellm.agent.linux.LinuxWorkspaceManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LinuxWorkspaceManagerTest {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private fun manager(): LinuxWorkspaceManager =
    LinuxWorkspaceManager(File(temporaryFolder.root, "linux_workspace"))

  @Test
  fun destructiveCommandsAreBlockedWithoutStartingAProcess() = runBlocking {
    val result = manager().executeCommand("rm -rf /")

    assertEquals(126, result.exitCode)
    assertTrue(result.stderr.contains("BLOCKED"))
  }

  @Test
  fun ordinaryCommandsAreNotBlockedByTheWordReboot() = runBlocking {
    val result = manager().executeCommand("printf reboot")

    assertEquals(0, result.exitCode)
    assertTrue(result.stdout.contains("reboot"))
  }

  @Test
  fun shellTimeoutTerminatesTheProcess() = runBlocking {
    val result = manager().executeCommand("sleep 5", timeoutMs = 250)

    assertEquals(124, result.exitCode)
    assertTrue(result.stderr.contains("TIMEOUT"))
  }

  @Test
  fun scriptNamesCannotEscapeTheWorkspace() {
    val script = manager().saveScript("../../escape.sh", "printf safe")

    val scriptsDir = File(temporaryFolder.root, "linux_workspace/scripts").canonicalFile
    assertEquals(scriptsDir, script.canonicalFile.parentFile)
  }

  @Test
  fun workspaceStatusDoesNotInventInstalledPackages() {
    val manager = manager()

    manager.installMinimalWorkspace()
    val status = manager.getStatus()

    assertTrue(status.isInstalled)
    assertEquals(0, status.totalPackagesInstalled)
    assertTrue(status.distro.contains("Local shell workspace"))
  }
}
