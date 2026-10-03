package com.ronan.heyboxlite

import org.json.JSONObject

internal data class ComposeProfileState(
    val loggedIn: Boolean = false,
    val userId: String = "",
    val name: String = "",
    val avatar: String = "",
    val hasProfile: Boolean = false,
    val follows: Int = 0,
    val fans: Int = 0,
    val likes: Int = 0,
    val signature: String = "",
) {
    fun sameAccount(other: ComposeProfileState): Boolean =
        loggedIn == other.loggedIn && userId == other.userId

    fun withProfile(body: JSONObject?, localIdentity: ComposeProfileState): ComposeProfileState {
        if (!sameAccount(localIdentity)) return localIdentity
        if (!loggedIn) return this
        val account = ProfileData.user(body) ?: return this
        return copy(
            name = Json.first(account.optString("username").trim(), localIdentity.name, name),
            avatar = Json.first(account.optString("avatar").trim(), localIdentity.avatar, avatar),
            hasProfile = true,
            follows = ProfileData.followCount(account),
            fans = ProfileData.fanCount(account),
            likes = ProfileData.likeCount(account),
            signature = account.optString("signature", "").trim(),
        )
    }

    companion object {
        fun cachedIdentity(
            loggedIn: Boolean,
            userId: String,
            name: String,
            avatar: String,
        ): ComposeProfileState = if (loggedIn) {
            ComposeProfileState(
                loggedIn = true,
                userId = userId,
                name = name,
                avatar = avatar,
            )
        } else ComposeProfileState()
    }
}
