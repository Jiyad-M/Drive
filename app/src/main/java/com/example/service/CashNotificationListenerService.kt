package com.example.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.model.CashAlertItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.regex.Pattern

class CashNotificationListenerService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private val TAG = "CashNotificationListener"
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        try {
            tts = TextToSpeech(applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            tts?.setPitch(1.05f)
            tts?.setSpeechRate(0.95f)
            isTtsReady = true
            Log.i(TAG, "TextToSpeech engine ready")
        } else {
            Log.w(TAG, "TextToSpeech initialization failed with status: $status")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceConnected.value = true
        activeInstance = this
        Log.i(TAG, "Cash Notification Listener Service connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isServiceConnected.value = false
        if (activeInstance == this) {
            activeInstance = null
        }
        Log.w(TAG, "Cash Notification Listener Service disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val combined = "$title $text $bigText $subText".trim()
        if (combined.isEmpty()) return

        parseAndAnnounceCashNotification(sbn.packageName, title, text, combined)
    }

    private fun parseAndAnnounceCashNotification(
        pkg: String,
        title: String,
        body: String,
        fullContent: String
    ) {
        if (!cashAnnouncementEnabled) return

        val lower = fullContent.lowercase(Locale.ROOT)

        // Check if message indicates cash or payment receipt
        val hasCashKeyword = lower.contains("received") ||
                lower.contains("credited") ||
                lower.contains("payment") ||
                lower.contains("paid you") ||
                lower.contains("cash") ||
                lower.contains("credit") ||
                lower.contains("added to your account") ||
                lower.contains("upi") ||
                lower.contains("deposited")

        if (!hasCashKeyword) return

        // Extract currency amount
        val amount = extractAmount(fullContent)
        if (amount.isNullOrEmpty()) return

        // Extract sender name
        val sender = extractSender(title, body)

        Log.i(TAG, "Cash payment detected from $pkg: Amount=$amount, Sender=$sender")

        val alertItem = CashAlertItem(
            id = System.currentTimeMillis(),
            amountText = amount,
            sender = sender,
            fullMessage = if (body.isNotEmpty()) body else fullContent
        )

        _lastCashAlert.value = alertItem

        if (cashTtsEnabled) {
            speakCashReceipt(amount, sender)
        }

        // Auto-dismiss alert popup after 9 seconds
        scope.launch {
            delay(9000)
            if (_lastCashAlert.value?.id == alertItem.id) {
                _lastCashAlert.value = null
            }
        }
    }

    fun speakCashReceipt(amount: String, sender: String) {
        if (!isTtsReady || tts == null) {
            Log.w(TAG, "TTS not ready to speak cash receipt")
            return
        }

        val speechText = if (sender.isNotEmpty() && sender != "Customer") {
            "Cash received. $amount Rupees from $sender"
        } else {
            "Cash received. $amount Rupees"
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "cash_tts_${System.currentTimeMillis()}")
            } else {
                @Suppress("DEPRECATION")
                tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking cash announcement: ${e.message}")
        }
    }

    private fun extractAmount(text: String): String? {
        // Match patterns like: ₹500, ₹ 1,250.00, Rs. 350, Rs 200, 500 INR, $45
        val patterns = listOf(
            Pattern.compile("""(?:₹|rs\.?|inr|\$)\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([\d,]+(?:\.\d{1,2})?)\s*(?:rupees|rs|inr)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""(?:received|credited|paid)\s*(?:of)?\s*(?:₹|rs\.?|inr|\$)?\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val match = matcher.group(1)?.replace(",", "")?.trim()
                if (!match.isNullOrEmpty()) {
                    return match
                }
            }
        }
        return null
    }

    private fun extractSender(title: String, body: String): String {
        val fromPattern = Pattern.compile("""from\s+([A-Za-z0-9\s]+?)(?:\s+(?:on|via|through|using|\.|\band\b|$))""", Pattern.CASE_INSENSITIVE)
        val matcherBody = fromPattern.matcher(body)
        if (matcherBody.find()) {
            return matcherBody.group(1)?.trim() ?: "Customer"
        }

        val matcherTitle = fromPattern.matcher(title)
        if (matcherTitle.find()) {
            return matcherTitle.group(1)?.trim() ?: "Customer"
        }

        if (title.isNotEmpty() && !title.lowercase().contains("payment") && !title.lowercase().contains("received")) {
            return title.take(24)
        }

        return "Customer"
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        activeInstance = null
        _isServiceConnected.value = false
    }

    companion object {
        var cashAnnouncementEnabled: Boolean = true
        var cashTtsEnabled: Boolean = true

        private var activeInstance: CashNotificationListenerService? = null

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected = _isServiceConnected.asStateFlow()

        private val _lastCashAlert = MutableStateFlow<CashAlertItem?>(null)
        val lastCashAlert = _lastCashAlert.asStateFlow()

        fun isNotificationServiceEnabled(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat != null && flat.contains(packageName)
        }

        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun triggerManualSimulation(amount: String = "450", sender: String = "Rahul Kumar") {
            val alert = CashAlertItem(
                id = System.currentTimeMillis(),
                amountText = amount,
                sender = sender,
                fullMessage = "Payment of ₹$amount received from $sender via Google Pay"
            )
            _lastCashAlert.value = alert
            activeInstance?.speakCashReceipt(amount, sender)
        }

        fun clearCurrentAlert() {
            _lastCashAlert.value = null
        }
    }
}
