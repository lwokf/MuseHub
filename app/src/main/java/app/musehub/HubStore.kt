package app.musehub

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Settings + token. Token lives in EncryptedSharedPreferences only, never in logs or code. */
class HubStore(context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "muse_hub_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("muse_hub_prefs_fallback", Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = prefs.getString("base_url", "http://192.3.90.237:3000") ?: "http://192.3.90.237:3000"
        set(v) { prefs.edit().putString("base_url", v.trim()).apply() }

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) { prefs.edit().putString("token", v.trim()).apply() }

    var agentId: String
        get() = prefs.getString("agent_id", "") ?: ""
        set(v) { prefs.edit().putString("agent_id", v.trim()).apply() }

    var pollSeconds: Int
        get() = prefs.getInt("poll_seconds", 45)
        set(v) { prefs.edit().putInt("poll_seconds", v).apply() }

    /** Last chosen input mode: "keyboard" or "voice". */
    var inputMode: String
        get() = prefs.getString("input_mode", "keyboard") ?: "keyboard"
        set(v) { prefs.edit().putString("input_mode", v).apply() }
}
