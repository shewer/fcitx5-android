package me.rocka.fcitx5test.data

import android.content.SharedPreferences
import android.content.res.Resources
import androidx.core.content.edit
import me.rocka.fcitx5test.R
import me.rocka.fcitx5test.keyboard.candidates.ExpandableCandidate
import me.rocka.fcitx5test.utils.WeakHashSet
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.cast

class Prefs(private val sharedPreferences: SharedPreferences, resources: Resources) {

    private val managedPreferences = mutableMapOf<String, ManagedPreference<*>>()

    private val onSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            managedPreferences[key]?.fireChange()
        }

    open inner class ManagedPreference<T : Any>(
        val key: String,
        val defaultValue: T,
        val type: KClass<T>
    ) : ReadWriteProperty<Any?, T> {
        private val listeners by lazy { WeakHashSet<OnChangeListener<T>>() }
        open var value: T
            get() = type.cast(sharedPreferences.all[key] ?: defaultValue)
            set(value) {
                when (value) {
                    is Boolean -> sharedPreferences.edit { putBoolean(key, value) }
                    is Long -> sharedPreferences.edit { putLong(key, value) }
                    is Float -> sharedPreferences.edit { putFloat(key, value) }
                    is Int -> sharedPreferences.edit { putInt(key, value) }
                    is String -> sharedPreferences.edit { putString(key, value) }
                }
            }

        // WARN: no anonymous listeners, please keep the reference!
        fun registerOnChangeListener(listener: OnChangeListener<T>) {
            listeners.add(listener)
        }

        fun unregisterOnChangeListener(listener: OnChangeListener<T>) {
            listeners.remove(listener)
        }

        fun fireChange() {
            listeners.forEach { with(it) { onChange() } }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.value = value
        }

        override fun toString(): String = "ManagedPreference[$key]($value:$type)"
    }

    interface StringLikeCodec<T : Any> {
        fun encode(x: T): String = x.toString()
        fun decode(raw: String): T?
    }


    inner class StringLikePreference<T : Any>(
        key: String,
        defaultValue: T,
        type: KClass<T>,
        val codec: StringLikeCodec<T>
    ) :
        ManagedPreference<T>(key, defaultValue, type) {
        override var value: T
            get() {
                val raw = sharedPreferences.all[key] ?: return defaultValue
                return (raw as? String)?.let { codec.decode(it) }
                    ?: throw RuntimeException("Failed to decode $raw")
            }
            set(value) {
                val encoded = codec.encode(value)
                sharedPreferences.edit { putString(key, encoded) }
            }

    }

    fun interface OnChangeListener<T : Any> {
        fun ManagedPreference<T>.onChange()
    }

    private inline fun <reified T : Any> preference(key: String, defaultValue: T) =
        if (key in managedPreferences)
            throw IllegalArgumentException("Preference with key $key is already defined: ${managedPreferences[key]}")
        else
            ManagedPreference(key, defaultValue, T::class).also {
                managedPreferences[key] = it
            }


    private inline fun <reified T : Any> stringLikePreference(
        key: String,
        defaultValue: T,
        codec: StringLikeCodec<T>
    ) = if (key in managedPreferences)
        throw IllegalArgumentException("Preference with key $key is already defined: ${managedPreferences[key]}")
    else
        StringLikePreference(key, defaultValue, T::class, codec).also {
            managedPreferences[key] = it
        }

    val firstRun = preference(resources.getString(R.string.pref_first_run), true)
    val ignoreSystemCursor =
        preference(resources.getString(R.string.pref_ignore_system_cursor), true)
    val hideKeyConfig = preference(resources.getString(R.string.pref_hide_key_config), true)
    val buttonHapticFeedback =
        preference(resources.getString(R.string.pref_button_haptic_feedback), true)
    val clipboard = preference(resources.getString(R.string.pref_clipboard_enable), true)
    val clipboardHistoryLimit = preference(resources.getString(R.string.pref_clipboard_limit), 5)
    val expandableCandidateStyle = stringLikePreference(
        resources.getString(R.string.pref_expandable_candidate_style),
        ExpandableCandidate.Style.Grid,
        ExpandableCandidate.Style
    )

    companion object {
        private var instance: Prefs? = null

        /**
         * MUST call before use
         */
        @Synchronized
        fun init(sharedPreferences: SharedPreferences, resources: Resources) {
            if (instance != null)
                return
            instance = Prefs(sharedPreferences, resources)
            sharedPreferences.registerOnSharedPreferenceChangeListener(getInstance().onSharedPreferenceChangeListener)
        }

        @Synchronized
        fun getInstance() = instance!!
    }
}