package com.ronan.heyboxlite

/** UI commands stay separate from the host's route and lifecycle ownership. */
internal fun ComposeAppHost.feedAction(item: FeedItem, action: FeedAction) {
    callbacks.feedAction(item, when (action) {
        FeedAction.LIKE -> ComposeAppCallbacks.ACTION_LIKE
        FeedAction.FAVORITE -> ComposeAppCallbacks.ACTION_FAVORITE
        FeedAction.CACHE -> ComposeAppCallbacks.ACTION_CACHE
        FeedAction.FOLLOW -> ComposeAppCallbacks.ACTION_FOLLOW
        FeedAction.COMMENT -> ComposeAppCallbacks.ACTION_COMMENT
    })
}

internal fun ComposeAppHost.detailAction(action: ComposeDetailAction) {
    when (action) {
        is ComposeDetailAction.LikePost -> callbacks.feedAction(action.item, ComposeAppCallbacks.ACTION_LIKE)
        is ComposeDetailAction.FavoritePost -> callbacks.feedAction(action.item, ComposeAppCallbacks.ACTION_FAVORITE)
        is ComposeDetailAction.CachePost -> callbacks.feedAction(action.item, ComposeAppCallbacks.ACTION_CACHE)
        is ComposeDetailAction.FollowAuthor -> callbacks.feedAction(action.item, ComposeAppCallbacks.ACTION_FOLLOW)
        is ComposeDetailAction.WriteComment -> detailComments.edit(null, "")
        is ComposeDetailAction.LikeComment -> detailComments.like(action.comment)
        is ComposeDetailAction.Reply -> detailComments.edit(action.comment, action.rootId)
        is ComposeDetailAction.LoadReplies -> detailComments.replies(action.root)
        is ComposeDetailAction.OpenCommentImage -> {
            val gallery = composeCommentGallery(action.comment, action.url)
            callbacks.requestImages(action.source, gallery.urls.toTypedArray(), gallery.index)
        }
    }
}
