package com.perpcorp.edgellm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.perpcorp.edgellm.agent.AgentToolRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgentSecurityTest {

  private fun registry(): AgentToolRegistry {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return AgentToolRegistry(context)
  }

  @Test
  fun restrictedToolIsDefaultDenyUntilApproved() = runBlocking {
    val registry = registry()

    val pending = registry.executeTool(
      "linux_shell_exec",
      mapOf("command" to "printf SHOULD_NOT_RUN")
    )

    assertTrue(pending.requiresUserConfirmation)
    assertFalse(pending.isSuccess)
    assertTrue(pending.output.contains("Approval required"))
    assertNotNull(registry.pendingApproval.value)

    val denied = registry.resolveApproval(false)
    assertFalse(denied.isSuccess)
    assertTrue(denied.output.contains("cancelled"))
  }

  @Test
  fun approvedToolRunsExactlyOnce() = runBlocking {
    val registry = registry()

    registry.executeTool("linux_shell_exec", mapOf("command" to "printf approved"))
    val approved = registry.resolveApproval(true)

    assertTrue(approved.isSuccess)
    assertTrue(approved.output.contains("approved"))
    assertNull(registry.pendingApproval.value)
    assertFalse(registry.resolveApproval(true).isSuccess)
  }
}
