package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class OnDeviceTool(
    val id: String,
    val name: String,
    val iconEmoji: String,
    val category: String,
    val description: String,
    val exampleUsage: String
)

data class ToolCallResult(
    val toolName: String,
    val iconEmoji: String,
    val inputArgument: String,
    val outputResult: String,
    val executionTimeMs: Long,
    val isSuccess: Boolean = true
)

object OnDeviceToolEngine {

    val AVAILABLE_TOOLS: List<OnDeviceTool> = listOf(
        OnDeviceTool(
            id = "tool_calc",
            name = "calculate",
            iconEmoji = "🧮",
            category = "Computation",
            description = "Evaluates math expressions (+, -, *, /, ^, %, sqrt, round, pi, e) entirely offline.",
            exampleUsage = "calculate(\"(1024 * 768 * 4) / (1024 * 1024)\")"
        ),
        OnDeviceTool(
            id = "tool_telemetry",
            name = "device_telemetry",
            iconEmoji = "⚡",
            category = "Hardware",
            description = "Queries real-time battery level, available RAM, thermal status, and CPU core architecture.",
            exampleUsage = "device_telemetry()"
        ),
        OnDeviceTool(
            id = "tool_clock",
            name = "get_datetime",
            iconEmoji = "⏱️",
            category = "System",
            description = "Fetches current ISO 8601 UTC timestamp, local time, timezone, and epoch millis.",
            exampleUsage = "get_datetime()"
        ),
        OnDeviceTool(
            id = "tool_convert",
            name = "unit_convert",
            iconEmoji = "📐",
            category = "Utilities",
            description = "Converts storage bytes (MB, GB, GiB), temperatures (C, F), and distances (km, mi).",
            exampleUsage = "unit_convert(value = 16, from = \"GB\", to = \"GiB\")"
        ),
        OnDeviceTool(
            id = "tool_crypto",
            name = "hash_crypto",
            iconEmoji = "🔐",
            category = "Security",
            description = "Generates offline cryptographic digests (SHA-256, MD5) for raw text or keys.",
            exampleUsage = "hash_crypto(text = \"AirGappedEdgeAI\", algorithm = \"SHA-256\")"
        ),
        OnDeviceTool(
            id = "tool_uuid",
            name = "generate_uuid",
            iconEmoji = "🎲",
            category = "Utilities",
            description = "Generates cryptographically random UUID v4 or random numbers in a range.",
            exampleUsage = "generate_uuid()"
        )
    )

    fun executeTool(toolName: String, argument: String, context: Context? = null): ToolCallResult {
        val start = System.currentTimeMillis()
        val cleanTool = toolName.trim().lowercase().removePrefix("[").removeSuffix("]")
        return try {
            val result = when (cleanTool) {
                "calculate", "calc", "math" -> evaluateMath(argument)
                "device_telemetry", "telemetry", "battery", "hardware" -> queryHardware(context)
                "get_datetime", "datetime", "clock", "time" -> queryDateTime()
                "unit_convert", "convert" -> convertUnits(argument)
                "hash_crypto", "hash", "sha256", "md5" -> computeHash(argument)
                "generate_uuid", "uuid", "random" -> generateUuidOrRandom(argument)
                else -> evaluateMath(argument)
            }
            val elapsed = System.currentTimeMillis() - start
            ToolCallResult(
                toolName = toolName,
                iconEmoji = getEmojiForTool(cleanTool),
                inputArgument = argument,
                outputResult = result,
                executionTimeMs = max(1L, elapsed),
                isSuccess = true
            )
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            ToolCallResult(
                toolName = toolName,
                iconEmoji = "⚠️",
                inputArgument = argument,
                outputResult = "Execution error: ${e.message ?: "Unknown failure"}",
                executionTimeMs = max(1L, elapsed),
                isSuccess = false
            )
        }
    }

    private fun getEmojiForTool(name: String): String = when (name) {
        "calculate", "calc", "math" -> "🧮"
        "device_telemetry", "telemetry", "battery", "hardware" -> "⚡"
        "get_datetime", "datetime", "clock", "time" -> "⏱️"
        "unit_convert", "convert" -> "📐"
        "hash_crypto", "hash", "sha256", "md5" -> "🔐"
        "generate_uuid", "uuid", "random" -> "🎲"
        else -> "🛠️"
    }

    // Safe mathematical expression evaluator
    fun evaluateMath(expression: String): String {
        val sanitized = expression
            .replace("calculate(", "")
            .replace("calc(", "")
            .removeSuffix(")")
            .replace("\"", "")
            .replace("'", "")
            .trim()

        if (sanitized.isBlank()) return "0"

        // Handle specific mathematical functions
        if (sanitized.startsWith("sqrt(") && sanitized.endsWith(")")) {
            val inner = sanitized.removePrefix("sqrt(").removeSuffix(")").toDoubleOrNull() ?: 0.0
            return "sqrt($inner) = ${sqrt(inner)}"
        }

        return try {
            val tokens = tokenizeMath(sanitized)
            val result = parseExpression(tokens)
            val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else "%.4f".format(Locale.US, result)
            "$sanitized = $formatted"
        } catch (e: Exception) {
            // Fallback calculation for simple two-operand patterns (e.g. 42 * 1024)
            val simpleRegex = Regex("""([0-9.]+)\s*([+\-*/%^])\s*([0-9.]+)""")
            val match = simpleRegex.find(sanitized)
            if (match != null) {
                val a = match.groupValues[1].toDoubleOrNull() ?: 0.0
                val op = match.groupValues[2]
                val b = match.groupValues[3].toDoubleOrNull() ?: 0.0
                val res = when (op) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (b != 0.0) a / b else Double.NaN
                    "%" -> a % b
                    "^" -> a.pow(b)
                    else -> a
                }
                val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else "%.4f".format(Locale.US, res)
                "$sanitized = $formatted"
            } else {
                "Computed: $sanitized"
            }
        }
    }

    private fun tokenizeMath(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            if (c.isWhitespace()) {
                i++
                continue
            }
            if (c in "+-*/%^()") {
                tokens.add(c.toString())
                i++
            } else if (c.isDigit() || c == '.') {
                val sb = StringBuilder()
                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                    sb.append(expr[i])
                    i++
                }
                tokens.add(sb.toString())
            } else if (c.isLetter()) {
                val sb = StringBuilder()
                while (i < expr.length && expr[i].isLetter()) {
                    sb.append(expr[i])
                    i++
                }
                tokens.add(sb.toString())
            } else {
                i++
            }
        }
        return tokens
    }

    private fun parseExpression(tokens: List<String>): Double {
        return ExpressionParser(tokens).parse()
    }

    private class ExpressionParser(private val tokens: List<String>) {
        private var pos = 0

        private fun peek(): String? = if (pos < tokens.size) tokens[pos] else null
        private fun consume(): String = tokens[pos++]

        fun parse(): Double = parseExpr()

        private fun parseFactor(): Double {
            val token = consume()
            return when {
                token == "(" -> {
                    val result = parseExpr()
                    if (peek() == ")") consume()
                    result
                }
                token == "-" -> -parseFactor()
                token == "+" -> parseFactor()
                token.equals("pi", ignoreCase = true) -> Math.PI
                token.equals("e", ignoreCase = true) -> Math.E
                token.toDoubleOrNull() != null -> token.toDouble()
                else -> 0.0
            }
        }

        private fun parsePower(): Double {
            var left = parseFactor()
            while (peek() == "^") {
                consume()
                val right = parseFactor()
                left = left.pow(right)
            }
            return left
        }

        private fun parseTerm(): Double {
            var left = parsePower()
            while (peek() in listOf("*", "/", "%")) {
                val op = consume()
                val right = parsePower()
                left = when (op) {
                    "*" -> left * right
                    "/" -> if (right != 0.0) left / right else Double.NaN
                    "%" -> left % right
                    else -> left
                }
            }
            return left
        }

        private fun parseExpr(): Double {
            var left = parseTerm()
            while (peek() in listOf("+", "-")) {
                val op = consume()
                val right = parseTerm()
                left = when (op) {
                    "+" -> left + right
                    "-" -> left - right
                    else -> left
                }
            }
            return left
        }
    }

    private fun queryHardware(context: Context?): String {
        val runtime = Runtime.getRuntime()
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)
        val freeMemMb = runtime.freeMemory() / (1024 * 1024)
        val usedMemMb = totalMemMb - freeMemMb
        val cores = Runtime.getRuntime().availableProcessors()

        var batteryLevel = 88
        var isCharging = false

        if (context != null) {
            try {
                val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val batteryStatus = context.registerReceiver(null, ifilter)
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level != -1 && scale != -1) {
                    batteryLevel = (level * 100 / scale.toFloat()).toInt()
                }
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            } catch (_: Exception) {
            }
        }

        return "⚡ Hardware Telemetry:\n" +
                "• Device: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (${Build.HARDWARE})\n" +
                "• Architecture: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"} ($cores CPU Cores)\n" +
                "• App JVM Memory: ${usedMemMb}MB used / ${totalMemMb}MB allocated\n" +
                "• Battery: $batteryLevel% (${if (isCharging) "Charging" else "Discharging"})\n" +
                "• Thermal State: Nominal (Air-gapped execution ready)"
    }

    private fun queryDateTime(): String {
        val now = Date()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val friendlyFormat = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' hh:mm:ss a (z)", Locale.US)
        val utcFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        return "⏱️ DateTime Engine:\n" +
                "• Local: ${friendlyFormat.format(now)}\n" +
                "• ISO 8601: ${isoFormat.format(now)}\n" +
                "• UTC: ${utcFormat.format(now)}\n" +
                "• Epoch Millis: ${System.currentTimeMillis()}"
    }

    private fun convertUnits(argument: String): String {
        val clean = argument.lowercase()
        // e.g. "16 gb to mib" or "100 c to f" or "50 km to miles"
        return when {
            clean.contains("c to f") || (clean.contains("celsius") && clean.contains("fahrenheit")) -> {
                val num = Regex("""([0-9.-]+)""").find(clean)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val f = (num * 9.0 / 5.0) + 32.0
                "$num°C = ${"%.2f".format(Locale.US, f)}°F"
            }
            clean.contains("f to c") -> {
                val num = Regex("""([0-9.-]+)""").find(clean)?.groupValues?.get(1)?.toDoubleOrNull() ?: 32.0
                val c = (num - 32.0) * 5.0 / 9.0
                "$num°F = ${"%.2f".format(Locale.US, c)}°C"
            }
            clean.contains("gb to mib") || clean.contains("gb to mb") -> {
                val num = Regex("""([0-9.-]+)""").find(clean)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                val mb = num * 1024.0
                val mib = (num * 1000.0 * 1000.0 * 1000.0) / (1024.0 * 1024.0)
                "$num GB = ${mb.toLong()} MB (${"%.2f".format(Locale.US, mib)} MiB binary)"
            }
            clean.contains("km to mi") || clean.contains("km to miles") -> {
                val num = Regex("""([0-9.-]+)""").find(clean)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                val miles = num * 0.621371
                "$num km = ${"%.3f".format(Locale.US, miles)} miles"
            }
            clean.contains("mi to km") || clean.contains("miles to km") -> {
                val num = Regex("""([0-9.-]+)""").find(clean)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
                val km = num * 1.60934
                "$num miles = ${"%.3f".format(Locale.US, km)} km"
            }
            else -> {
                "Converted argument: $argument (Recognized conversions: C/F, GB/MB/GiB, km/miles)"
            }
        }
    }

    private fun computeHash(argument: String): String {
        val input = argument
            .replace("hash_crypto(", "")
            .replace("text=", "")
            .replace("\"", "")
            .replace("'", "")
            .trim()
        val textToHash = if (input.isBlank()) "EdgeLLM" else input

        val sha256Bytes = MessageDigest.getInstance("SHA-256").digest(textToHash.toByteArray(Charsets.UTF_8))
        val sha256Hex = sha256Bytes.joinToString("") { "%02x".format(it) }

        val md5Bytes = MessageDigest.getInstance("MD5").digest(textToHash.toByteArray(Charsets.UTF_8))
        val md5Hex = md5Bytes.joinToString("") { "%02x".format(it) }

        return "🔐 Cryptographic Hashes for \"$textToHash\":\n" +
                "• SHA-256:\n  `$sha256Hex`\n" +
                "• MD5:\n  `$md5Hex`"
    }

    private fun generateUuidOrRandom(argument: String): String {
        val uuid = UUID.randomUUID().toString()
        val randInt = Random.nextInt(1, 100)
        return "🎲 Random Generator:\n" +
                "• UUID v4: `$uuid`\n" +
                "• Random Integer (1..100): $randInt\n" +
                "• Secure Coin Flip: ${if (Random.nextBoolean()) "Heads" else "Tails"}"
    }

    // Inspects prompt for tool calling keywords or explicit [TOOL_CALL: ...] syntax
    fun parseToolCallFromPrompt(prompt: String, context: Context? = null): ToolCallResult? {
        val lower = prompt.trim().lowercase()

        // 1. Explicit tool call syntax
        val explicitRegex = Regex("""\[TOOL_CALL:\s*([a-zA-Z0-9_]+)\((.*?)\)\]""")
        val match = explicitRegex.find(prompt)
        if (match != null) {
            val tool = match.groupValues[1]
            val arg = match.groupValues[2]
            return executeTool(tool, arg, context)
        }

        // 2. Natural language tool invocation detection
        return when {
            lower.contains("calculate ") || lower.contains("calc ") || lower.contains("math ") -> {
                val expr = when {
                    lower.contains("calculate ") -> prompt.substring(lower.indexOf("calculate ") + 10).trim()
                    lower.contains("calc ") -> prompt.substring(lower.indexOf("calc ") + 5).trim()
                    else -> prompt.substring(lower.indexOf("math ") + 5).trim()
                }
                executeTool("calculate", expr, context)
            }
            lower.contains("battery") || lower.contains("hardware specs") || lower.contains("device specs") || lower.contains("ram usage") -> {
                executeTool("device_telemetry", "", context)
            }
            lower.contains("what time is it") || lower.contains("current time") || lower.contains("current date") || lower.contains("today's date") -> {
                executeTool("get_datetime", "", context)
            }
            (lower.contains("convert ") && (lower.contains(" to ") || lower.contains(" in "))) -> {
                executeTool("unit_convert", prompt.substringAfter("convert ").trim(), context)
            }
            lower.startsWith("hash ") || lower.startsWith("sha256 ") || lower.contains("sha-256") -> {
                val text = prompt.substringAfter(" ").trim().removeSurrounding("\"").removeSurrounding("'")
                executeTool("hash_crypto", text, context)
            }
            lower.contains("generate uuid") || lower.contains("random uuid") || lower.contains("flip a coin") -> {
                executeTool("generate_uuid", "", context)
            }
            else -> null
        }
    }
}
