package com.ronan.heyboxlite

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject

internal data class ComposeUserSpaceState(
    val loading: Boolean = true,
    val name: String = "小黑盒用户",
    val avatar: String = "",
    val signature: String = "",
    val follows: Int = 0,
    val fans: Int = 0,
    val likes: Int = 0,
    val items: List<FeedItem> = emptyList(),
    val error: String = "",
)

/** Owns user-space loading and ignores responses belonging to a previous route. */
internal class ComposeUserSpaceController(
    private val api: ApiClient,
    private val cache: LocalCache,
) {
    val state: MutableState<ComposeUserSpaceState> = mutableStateOf(ComposeUserSpaceState())
    private var requestSerial = 0

    fun load(route: ComposeUserSpaceRoute) {
        val serial = ++requestSerial
        if (route.userId.isBlank()) {
            state.value = ComposeUserSpaceState(
                loading = false,
                name = route.name.ifBlank { "小黑盒用户" },
                avatar = route.avatar,
                error = "缺少用户 ID",
            )
            return
        }
        state.value = ComposeUserSpaceState(
            name = route.name.ifBlank { "小黑盒用户" },
            avatar = route.avatar,
        )
        api.get(
            EndpointProvider.profileUserLinks(),
            OfficialRequestParams.profileLinks(route.userId, 0, 20),
            object : ApiClient.Callback {
                override fun onSuccess(body: JSONObject) {
                    if (serial != requestSerial) return
                    val user = ProfileData.user(body)
                    val array = ProfileData.posts(body)
                    val posts = ArrayList<FeedItem>()
                    if (array != null) {
                        for (index in 0 until array.length()) {
                            val raw = array.optJSONObject(index) ?: continue
                            val link = raw.optJSONObject("link") ?: raw
                            posts.add(FeedItem.from(link))
                        }
                    }
                    state.value = ComposeUserSpaceState(
                        loading = false,
                        name = Json.first(
                            user?.optString("username"), user?.optString("nickname"),
                            user?.optString("name"), route.name, "小黑盒用户",
                        ),
                        avatar = Json.first(
                            user?.optString("avatar"), user?.optString("avartar"), route.avatar,
                        ),
                        signature = Json.first(user?.optString("signature"), user?.optString("desc")),
                        follows = ProfileData.followCount(user),
                        fans = ProfileData.fanCount(user),
                        likes = ProfileData.likeCount(user),
                        items = posts,
                    )
                }

                override fun onError(message: String) {
                    if (serial != requestSerial) return
                    state.value = state.value.copy(loading = false, error = "动态加载失败")
                    cache.log("compose user space failed: $message")
                }
            },
        )
    }

    fun close() {
        requestSerial++
    }
}
