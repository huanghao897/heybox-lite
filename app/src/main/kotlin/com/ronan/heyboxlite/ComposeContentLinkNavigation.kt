package com.ronan.heyboxlite

import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONObject

/** All inline content shares the host's existing post/profile navigation owners. */
internal val LocalComposeContentLinkNavigation =
    staticCompositionLocalOf<((OfficialContentLink) -> Unit)?> { null }

internal fun ComposeAppHost.openContentLink(link: OfficialContentLink) {
    when (link.kind) {
        OfficialContentLink.Kind.POST -> openDetail(FeedItem.from(JSONObject()
            .put("linkid", link.id).put("h_src", link.hsrc)))
        OfficialContentLink.Kind.USER -> navigate("user_space?user=${Uri.encode(link.id)}")
    }
}
