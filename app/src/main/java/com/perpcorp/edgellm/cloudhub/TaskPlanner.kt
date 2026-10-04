package com.perpcorp.edgellm.cloudhub

import android.content.Context
import android.util.Log
import com.perpcorp.edgellm.data.model.HardwareAccelerationSettings
import com.perpcorp.edgellm.data.model.ModelSpec
import com.perpcorp.edgellm.plugin.PluginRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Goal→plan→steps pipeline ported from PrivateLM `task_controller.dart`.
 *
 * Two deliberate, documented divergences from the source:
 * 1. PrivateLM's planner asks for raw ADB shell commands and its own
 *    `executeTask` is a stub ("Command execution is not available").
 *    EdgeLLM is sandboxed and must NEVER run raw shell: [mapToPluginAction]
 *    only admits `plugin:<id>` / `mcp:<server>/<tool>` actions against the
 *    whitelisted [PluginRegistry]/MCP tools, and everything else resolves to
 *    [PlannedAction.Blocked] with an explanation.
 * 2. Persistence is a private JSON file ([PlanTaskStore]), not Hive/Room —
 *    EdgeLLM's Room schema stays untouched (no migration risk).
 */
data class PlanStep(
    val index: Int,
    val description: String,
    val command: String,
    val status: String = "pending",
    val output: String? = null,
)

data class PlannerTask(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,
    val status: String = "planning",
    val steps: List<PlanStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

/** A parsed step resolved against EdgeLLM's sandboxed tool surface. */
sealed interface PlannedAction {
    /** Safe to execute: a registered plugin id plus its input. */
    data class Plugin(val pluginId: String, val input: String) : PlannedAction

    /** Refused: raw shell/ADB/su or unknown tool. Carries the reason. */
    data class Blocked(val command: String, val reason: String) : PlannedAction
}

object TaskPlanner {

    private const val TAG = "TaskPlanner"

    /**
     * Plan prompt. Adapted from PrivateLM: same numbered + `CMD:` contract so
     * [parsePlanSteps] stays compatible, but scoped to EdgeLLM's sandboxed
     * plugin actions instead of ADB shell (which this app cannot and must
     * not execute).
     */
    fun buildPlanPrompt(goal: String, availablePluginIds: List<String>): String {
        val tools = if (availablePluginIds.isEmpty()) {
            "(none registered)"
        } else {
            availablePluginIds.joinToString("\n") { "- plugin:$it" }
        }
        return """
            You are an on-device task planner. Given a user's goal, break it into steps this sandboxed assistant can perform. You may ONLY use the registered plugin actions below. Never invent shell commands, ADB commands, URLs, or tools.

            Output ONLY a numbered list. Each step has a short description line, then the exact action on a CMD: line:
            CMD: plugin:<plugin-id> | input: <text for the plugin to process>

            Available plugin actions:
            $tools

            Example:
            1. Redact personal data from the pasted text
            CMD: plugin:plugin_pii_redactor | input: <the text>

            User Goal: $goal

            Steps:
        """.trimIndent()
    }

    /** Step parser — same algorithm as PrivateLM `_parseSteps`. */
    fun parsePlanSteps(raw: String): List<PlanStep> {
        val steps = mutableListOf<PlanStep>()
        var currentDesc: String? = null
        var index = 0
        for (rawLine in raw.trim().lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val numMatch = Regex("""^\d+[.)]\s*(.+)""").find(line)
            if (numMatch != null) {
                currentDesc = numMatch.groupValues[1].trim()
                continue
            }
            if (line.startsWith("CMD:")) {
                val cmd = line.removePrefix("CMD:").trim()
                if (cmd.isNotEmpty()) {
                    steps.add(
                        PlanStep(
                            index = index++,
                            description = currentDesc ?: "Step $index",
                            command = cmd,
                        ),
                    )
                    currentDesc = null
                }
            }
        }
        return steps
    }

    /**
     * Resolve one parsed step to an executable-or-blocked action.
     *
     * Accepted: `plugin:<id> [| input: <text>]` and `mcp:<server>/<tool>
     * [| input: <json>]` where the id/server is registered AND on the
     * [allowedPluginIds] whitelist. Everything else — especially anything
     * resembling shell (`adb`, `sh`, `su`, `cmd`, pipes, redirects) — is
     * blocked. There is intentionally no raw-executor anywhere in this file.
     */
    fun mapToPluginAction(
        step: PlanStep,
        allowedPluginIds: Set<String>,
        mcpServers: Set<String> = emptySet(),
    ): PlannedAction {
        val cmd = step.command.trim()

        val shellSignals = listOf(
            "adb", "shell", "/bin/", "/system/bin", "su ", "sudo",
            "settings put", "cmd uimode", "|", "&&", ";", "`", "$(",
            ">", "chmod", "rm ", "reboot", "setprop",
        )
        val lowered = cmd.lowercase()
        val hit = shellSignals.firstOrNull { lowered.contains(it) }
        if (hit != null) {
            return PlannedAction.Blocked(
                cmd,
                "Refused: looks like raw shell/ADB (matched `$hit`). " +
                    "EdgeLLM executes plugin tools only, never shell.",
            )
        }

        val pluginMatch = Regex("""^plugin:([A-Za-z0-9_\-]+)\s*(?:\|\s*input:\s*(.*))?$""", RegexOption.DOT_MATCHES_ALL)
            .find(cmd)
        if (pluginMatch != null) {
            val id = pluginMatch.groupValues[1]
            val input = pluginMatch.groupValues[2].ifBlank { step.description }
            if (id !in allowedPluginIds) {
                return PlannedAction.Blocked(cmd, "Refused: plugin `$id` is not registered/whitelisted.")
            }
            return PlannedAction.Plugin(id, input)
        }

        val mcpMatch = Regex("""^mcp:([A-Za-z0-9_\-]+)/([A-Za-z0-9_\-]+)\s*(?:\|\s*input:\s*(.*))?$""", RegexOption.DOT_MATCHES_ALL)
            .find(cmd)
        if (mcpMatch != null) {
            val server = mcpMatch.groupValues[1]
            if (server !in mcpServers) {
                return PlannedAction.Blocked(cmd, "Refused: MCP server `$server` is not connected.")
            }
            return PlannedAction.Plugin("mcp:$server/${mcpMatch.groupValues[2]}", mcpMatch.groupValues[3])
        }

        return PlannedAction.Blocked(
            cmd,
            "Refused: unrecognized action. Use `plugin:<id> | input: ...` with a registered plugin.",
        )
    }

    /**
     * Execute one resolved action. MCP `mcp:server/tool` ids are returned
     * unexecuted with routing info — the caller (PluginPipelineScreen, which
     * owns [com.perpcorp.edgellm.plugin.McpClientManager]) performs the call
     * with user-visible confirmation.
     */
    suspend fun executeAction(
        action: PlannedAction,
        registry: PluginRegistry,
        model: ModelSpec,
        settings: HardwareAccelerationSettings,
    ): String {
        return when (action) {
            is PlannedAction.Blocked -> "BLOCKED: ${action.reason}\nCommand was: ${action.command}"
            is PlannedAction.Plugin -> {
                if (action.pluginId.startsWith("mcp:")) {
                    return " ROUTE_TO_MCP:${action.pluginId.removePrefix("mcp:")}\nInput: ${action.input}\n" +
                        "(Confirm in Plugin Pipeline before execution.)"
                }
                try {
                    val result = registry.executePlugin(action.pluginId, action.input, model, settings)
                        ?: return "ERROR: plugin `${action.pluginId}` disappeared."
                    if (result.success) result.processedOutput else "ERROR: plugin failed: ${result.processedOutput}"
                } catch (e: Exception) {
                    Log.w(TAG, "Plugin execution failed", e)
                    "ERROR: plugin `${action.pluginId}` threw: ${e.message}"
                }
            }
        }
    }
}

/**
 * File-backed planner-task store (private JSON, no Room migration).
 * Mirrors the Hive tasks box role (save/list/delete) without touching
 * EdgeLLM's Room schema.
 */
class PlanTaskStore(context: Context) {

    private val file = File(context.filesDir, "cloudhub_plans.json")
    private val lock = Any()

    fun loadAll(): List<PlannerTask> = synchronized(lock) {
        try {
            if (!file.isFile) return emptyList()
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
            }.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            Log.w(TAG, "Plan store read failed", e)
            emptyList()
        }
    }

    fun save(task: PlannerTask) = synchronized(lock) {
        try {
            val kept = loadAllUnsafe().filterNot { it.id == task.id } + task
            val arr = JSONArray()
            kept.forEach { arr.put(toJson(it)) }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Plan store write failed", e)
        }
    }

    fun delete(id: String) = synchronized(lock) {
        try {
            val kept = loadAllUnsafe().filterNot { it.id == id }
            val arr = JSONArray()
            kept.forEach { arr.put(toJson(it)) }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Plan store delete failed", e)
        }
    }

    private fun loadAllUnsafe(): List<PlannerTask> {
        if (!file.isFile) return emptyList()
        val arr = JSONArray(file.readText())
        return (0 until arr.length()).mapNotNull { i ->
            runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
        }
    }

    private fun toJson(t: PlannerTask): JSONObject {
        val steps = JSONArray()
        t.steps.forEach { s ->
            steps.put(
                JSONObject()
                    .put("index", s.index)
                    .put("description", s.description)
                    .put("command", s.command)
                    .put("status", s.status)
                    .put("output", s.output),
            )
        }
        return JSONObject()
            .put("id", t.id)
            .put("goal", t.goal)
            .put("status", t.status)
            .put("createdAt", t.createdAt)
            .put("steps", steps)
    }

    private fun fromJson(o: JSONObject): PlannerTask {
        val steps = mutableListOf<PlanStep>()
        val arr = o.optJSONArray("steps") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            steps.add(
                PlanStep(
                    index = s.optInt("index", i),
                    description = s.optString("description", "Step $i"),
                    command = s.optString("command", ""),
                    status = s.optString("status", "pending"),
                    output = s.optString("output", null),
                ),
            )
        }
        return PlannerTask(
            id = o.optString("id", UUID.randomUUID().toString()),
            goal = o.optString("goal", ""),
            status = o.optString("status", "pending"),
            steps = steps,
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )
    }

    companion object {
        private const val TAG = "PlanTaskStore"
    }
}
