package com.example.agent.tools

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import com.example.agent.AgentSecurityLevel
import com.example.agent.AgentTool
import com.example.agent.AgentToolCategory
import com.example.agent.AgentToolParameter
import com.example.agent.AgentToolResult

/**
 * Suite of Communication & PIM Productivity tools ported from rikkahub-agent:
 * - pim_create_calendar_event
 * - pim_set_timer
 * - pim_set_alarm
 * - pim_compose_email
 * - pim_send_sms
 * - pim_open_dialer
 * - pim_share_text
 * - pim_search_web
 * - pim_open_maps
 * - pim_open_contacts
 */
object CommunicationProductivityTools {

    val allTools: List<AgentTool> = listOf(
        CreateCalendarEventTool(),
        SetTimerTool(),
        SetAlarmTool(),
        ComposeEmailTool(),
        SendSmsTool(),
        OpenDialerTool(),
        ShareTextTool(),
        SearchWebTool(),
        OpenMapsTool(),
        OpenContactsTool()
    )

    class CreateCalendarEventTool : AgentTool {
        override val id = "pim_create_calendar_event"
        override val name = "Schedule Calendar Event"
        override val description = "Prepares an event on the system calendar with title, description, and time window."
        override val category = AgentToolCategory.PRODUCTIVITY
        override val securityLevel = AgentSecurityLevel.NORMAL
        override val parameters = listOf(
            AgentToolParameter("title", "string", "Title or summary of the calendar event", required = true),
            AgentToolParameter("duration_mins", "number", "Duration in minutes (e.g. 30, 60)", required = false, defaultValue = "60"),
            AgentToolParameter("description", "string", "Optional details or meeting agenda", required = false)
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val title = arguments["title"] ?: "Meeting"
            val durationMins = arguments["duration_mins"]?.toLongOrNull() ?: 60L
            val description = arguments["description"] ?: ""

            val startTime = System.currentTimeMillis() + (3600_000L) // in 1 hour
            val endTime = startTime + (durationMins * 60_000L)

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.Events.DESCRIPTION, description)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Prepared Calendar Event: \"$title\" for $durationMins minutes.",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Failed to launch calendar intent: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class SetTimerTool : AgentTool {
        override val id = "pim_set_timer"
        override val name = "Set Countdown Timer"
        override val description = "Starts a countdown timer via the system clock application."
        override val category = AgentToolCategory.PRODUCTIVITY
        override val securityLevel = AgentSecurityLevel.NORMAL
        override val parameters = listOf(
            AgentToolParameter("seconds", "number", "Timer duration in seconds", required = true),
            AgentToolParameter("message", "string", "Label or message for the timer", required = false, defaultValue = "Timer")
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val seconds = arguments["seconds"]?.toIntOrNull() ?: 300
            val message = arguments["message"] ?: "Timer"

            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Countdown timer set for $seconds seconds ($message).",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Failed to start clock timer: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class SetAlarmTool : AgentTool {
        override val id = "pim_set_alarm"
        override val name = "Set System Alarm"
        override val description = "Schedules a morning or reminder alarm in the system clock."
        override val category = AgentToolCategory.PRODUCTIVITY
        override val securityLevel = AgentSecurityLevel.NORMAL
        override val parameters = listOf(
            AgentToolParameter("hour", "number", "Hour of day in 24h format (0-23)", required = true),
            AgentToolParameter("minutes", "number", "Minutes (0-59)", required = true),
            AgentToolParameter("message", "string", "Alarm title / reason", required = false, defaultValue = "Agent Alarm")
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val hour = arguments["hour"]?.toIntOrNull() ?: 8
            val minutes = arguments["minutes"]?.toIntOrNull() ?: 0
            val message = arguments["message"] ?: "Agent Alarm"

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Alarm set for %02d:%02d ($message).".format(hour, minutes),
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Could not schedule alarm: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class ComposeEmailTool : AgentTool {
        override val id = "pim_compose_email"
        override val name = "Compose Email Draft"
        override val description = "Prepares an email in the user's default email client (Gmail/Outlook/K-9)."
        override val category = AgentToolCategory.COMMUNICATION
        override val securityLevel = AgentSecurityLevel.ELEVATED
        override val parameters = listOf(
            AgentToolParameter("recipient", "string", "Recipient email address", required = true),
            AgentToolParameter("subject", "string", "Email subject line", required = true),
            AgentToolParameter("body", "string", "Email body content", required = false, defaultValue = "")
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val recipient = arguments["recipient"] ?: ""
            val subject = arguments["subject"] ?: ""
            val body = arguments["body"] ?: ""

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Composed email draft to $recipient: \"$subject\".",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "No email application found to handle mailto intent: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class SendSmsTool : AgentTool {
        override val id = "pim_send_sms"
        override val name = "Compose SMS Message"
        override val description = "Prepares an SMS text message in the user's default messaging app."
        override val category = AgentToolCategory.COMMUNICATION
        override val securityLevel = AgentSecurityLevel.ELEVATED
        override val parameters = listOf(
            AgentToolParameter("phone_number", "string", "Phone number or contact", required = true),
            AgentToolParameter("message", "string", "Body of SMS message", required = true)
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val phone = arguments["phone_number"] ?: ""
            val msg = arguments["message"] ?: ""

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phone")
                putExtra("sms_body", msg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Drafted SMS to $phone: \"$msg\".",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Failed to launch SMS intent: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class OpenDialerTool : AgentTool {
        override val id = "pim_open_dialer"
        override val name = "Open Phone Dialer"
        override val description = "Opens the phone dialer with a pre-filled number ready for the user to tap call."
        override val category = AgentToolCategory.COMMUNICATION
        override val securityLevel = AgentSecurityLevel.NORMAL
        override val parameters = listOf(
            AgentToolParameter("phone_number", "string", "Number to dial", required = true)
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val phone = arguments["phone_number"] ?: ""
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$phone")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Phone dialer opened with $phone.",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Could not open dialer: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class ShareTextTool : AgentTool {
        override val id = "pim_share_text"
        override val name = "Share Text via Android Sheet"
        override val description = "Broadcasts text to other Android apps using the native Intent.ACTION_SEND share sheet."
        override val category = AgentToolCategory.COMMUNICATION
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = listOf(
            AgentToolParameter("text", "string", "Text content to share", required = true),
            AgentToolParameter("title", "string", "Optional chooser title", required = false, defaultValue = "Share via")
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val text = arguments["text"] ?: ""
            val title = arguments["title"] ?: "Share via"

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(sendIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(chooser)
                AgentToolResult(
                    isSuccess = true,
                    output = "Opened Android Share Sheet with ${text.length} characters.",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Could not trigger share sheet: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class SearchWebTool : AgentTool {
        override val id = "pim_search_web"
        override val name = "Search Web Browser"
        override val description = "Executes a query using the system default web browser or Google Search intent."
        override val category = AgentToolCategory.PRODUCTIVITY
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = listOf(
            AgentToolParameter("query", "string", "Search query or URL", required = true)
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val query = arguments["query"] ?: ""

            val intent = if (query.startsWith("http://") || query.startsWith("https://")) {
                Intent(Intent.ACTION_VIEW, Uri.parse(query))
            } else {
                Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(android.app.SearchManager.QUERY, query)
                }
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Initiated web search for: \"$query\".",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Failed to launch web browser: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class OpenMapsTool : AgentTool {
        override val id = "pim_open_maps"
        override val name = "Open Navigation & Maps"
        override val description = "Launches Google Maps or default mapping app to locate an address or GPS coordinates."
        override val category = AgentToolCategory.PRODUCTIVITY
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = listOf(
            AgentToolParameter("location", "string", "Address, place name, or lat,lng coordinates", required = true)
        )

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val loc = arguments["location"] ?: ""
            val gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(loc))
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(mapIntent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Opened Maps location for: \"$loc\".",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Could not open maps application: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }

    class OpenContactsTool : AgentTool {
        override val id = "pim_open_contacts"
        override val name = "Open Contacts Directory"
        override val description = "Navigates to the Android Contacts directory to view or pick an entry."
        override val category = AgentToolCategory.COMMUNICATION
        override val securityLevel = AgentSecurityLevel.SAFE
        override val parameters = emptyList<AgentToolParameter>()

        override suspend fun execute(context: Context, arguments: Map<String, String>): AgentToolResult {
            val start = System.currentTimeMillis()
            val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            return try {
                context.startActivity(intent)
                AgentToolResult(
                    isSuccess = true,
                    output = "Opened Android Contacts app.",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                AgentToolResult(
                    isSuccess = false,
                    output = "Failed to launch contacts: ${e.message}",
                    executionTimeMs = System.currentTimeMillis() - start
                )
            }
        }
    }
}
