package com.ronan.heyboxlite

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

/** Shared by the live profile page and transition previews; only the host starts requests. */
internal class ComposeProfileController(
    private val readIdentity: () -> ComposeProfileState,
    private val requestProfile: (String, Map<String, String>, ApiClient.Callback) -> Unit,
) {
    constructor(services: ComposeServices) : this(
        readIdentity = {
            val session = services.session
            ComposeProfileState.cachedIdentity(
                session.isLoggedIn(), session.userId(), session.userName(), session.avatar(),
            )
        },
        requestProfile = services.api::get,
    )

    val state: MutableState<ComposeProfileState> = mutableStateOf(readIdentity())
    private var requestSerial = 0
    private var inFlightUserId: String? = null
    private var closed = false

    fun refresh() {
        if (closed) return
        val identity = readIdentity()
        if (!state.value.sameAccount(identity)) {
            reset(identity)
        } else if (!state.value.hasProfile) {
            state.value = identity
        }
        if (!identity.loggedIn || identity.userId.isBlank()) return
        if (inFlightUserId == identity.userId) return

        val serial = ++requestSerial
        inFlightUserId = identity.userId
        requestProfile(
            EndpointProvider.profileUserLinks(),
            OfficialRequestParams.profileLinks(identity.userId, 0, 1),
            object : ApiClient.Callback {
                override fun onSuccess(body: JSONObject) {
                    val localIdentity = acceptResponse(serial, identity) ?: return
                    state.value = state.value.withProfile(body, localIdentity)
                }

                override fun onError(message: String) {
                    acceptResponse(serial, identity)
                }
            },
        )
    }

    fun invalidate() {
        if (!closed) reset(readIdentity())
    }

    fun close() {
        closed = true
        requestSerial++
        inFlightUserId = null
    }

    private fun reset(identity: ComposeProfileState) {
        requestSerial++
        inFlightUserId = null
        state.value = identity
    }

    private fun acceptResponse(serial: Int, identity: ComposeProfileState): ComposeProfileState? {
        if (closed || serial != requestSerial) return null
        val currentIdentity = readIdentity()
        if (!identity.sameAccount(currentIdentity)) {
            reset(currentIdentity)
            return null
        }
        inFlightUserId = null
        return currentIdentity
    }
}
