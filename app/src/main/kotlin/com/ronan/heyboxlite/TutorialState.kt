package com.ronan.heyboxlite

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

internal class TutorialStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("usage_tutorials", Context.MODE_PRIVATE)

    fun completed(topic: TutorialTopic): Boolean = preferences.getInt(topic.key, 0) >= topic.revision

    fun complete(topic: TutorialTopic) {
        preferences.edit { putInt(topic.key, topic.revision) }
    }
}

/** A visit may be deferred, but only explicit completion persists across application restarts. */
internal class TutorialState(private val store: TutorialStore) {
    var topic by mutableStateOf<TutorialTopic?>(null)
        private set
    private var replaying = false
    var rotary by mutableStateOf<ComposeRotaryRequest?>(null)
        private set
    private val deferred = mutableSetOf<TutorialTopic>()

    fun offer(value: TutorialTopic) {
        if (topic == null && value !in deferred && !store.completed(value)) {
            replaying = false
            rotary = null
            topic = value
        }
    }

    fun leave(value: TutorialTopic) {
        if (!replaying && topic == value) topic = null
    }

    fun replay(value: TutorialTopic) {
        replaying = true
        rotary = null
        topic = value
    }

    fun complete() {
        topic?.let(store::complete)
        topic = null
        replaying = false
    }

    fun dismiss(): Boolean {
        val current = topic ?: return false
        deferred += current
        topic = null
        replaying = false
        return true
    }

    fun scrollRotary(distance: Int): Boolean {
        if (topic == null) return false
        rotary = ComposeRotaryRequest((rotary?.serial ?: 0) + 1, distance)
        return true
    }
}
