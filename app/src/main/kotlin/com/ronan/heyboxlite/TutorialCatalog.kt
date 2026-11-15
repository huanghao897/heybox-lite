package com.ronan.heyboxlite

import androidx.annotation.DrawableRes

internal enum class TutorialTopic(val key: String, val title: String, @DrawableRes val icon: Int) {
    HOME("home", "首页操作", R.drawable.ic_nav_home),
    DETAIL("detail", "帖子与评论", R.drawable.il_reply),
    PROFILE("profile", "我的与阅读", R.drawable.ic_nav_profile),
    SEARCH("search", "搜索", R.drawable.il_search),
    IMAGES("images", "查看图片", R.drawable.il_zoom),
    CHECKIN("checkin", "自动签到入门", R.drawable.il_calendar),
    SERVICE_LOGIN("service_login", "登录签到服务", R.drawable.il_person),
    SERVICE_REGISTER("service_register", "注册签到服务", R.drawable.il_person),
    HEYBOX_LOGIN("heybox_login", "登录签到用的小黑盒账号", R.drawable.il_person);

    val revision: Int get() = 1
}

internal data class TutorialStep(val title: String, val body: String, @DrawableRes val icon: Int)

/** Instructions describe existing gestures; they never send requests or simulate account actions. */
internal object TutorialCatalog {
    fun routeTopic(route: String): TutorialTopic? = when (route.substringBefore('?')) {
        "feed" -> TutorialTopic.HOME
        "profile" -> TutorialTopic.PROFILE
        "search" -> TutorialTopic.SEARCH
        "detail" -> TutorialTopic.DETAIL
        "checkin_center" -> TutorialTopic.CHECKIN
        else -> null
    }

    fun steps(topic: TutorialTopic, doubleTapReply: Boolean = true): List<TutorialStep> = when (topic) {
        TutorialTopic.HOME -> listOf(
            TutorialStep("下拉，换一批帖子", "先滑到列表顶部，再向下拉并松手即可刷新。继续向下浏览会自动加载更多帖子。", R.drawable.il_refresh),
            TutorialStep("再点一次首页", "已经在首页时，再点底部胶囊里的首页图标，会回到顶部并刷新。", R.drawable.ic_nav_home),
            TutorialStep("左右切换", "在首页向左滑进入“我的”；在“我的”向右滑回到首页。底部胶囊也能切换这两个页面。", R.drawable.il_swipe),
        )
        TutorialTopic.DETAIL -> listOf(
            TutorialStep("滑到评论区", "在正文上向左滑，进入独立评论页；在评论页向右滑，回到正文。在正文向右滑则返回上一个页面。", R.drawable.il_swipe),
            TutorialStep("长按头像看主页", "长按帖子作者或评论用户的头像，即可进入对方主页。轻点头像不会跳走。", R.drawable.il_person),
            TutorialStep("双击评论回复", if (doubleTapReply) "登录后双击评论正文即可回复。长按评论文字可复制；点“展开回复”胶囊继续看楼中楼。"
                else "你已关闭双击回复，可在“设置 → 内容与缓存”中开启。长按评论文字可复制；点“展开回复”继续看楼中楼。", R.drawable.il_reply),
            TutorialStep("蓝色链接也能点", "评论里的帖子链接会在应用内打开，@ 用户链接会进入对方主页。抽奖等网页链接使用浏览器打开。", R.drawable.il_globe),
        )
        TutorialTopic.PROFILE -> listOf(
            TutorialStep("点账号卡片看动态", "登录后点击顶部账号信息卡片，可以查看自己的动态和投稿。", R.drawable.il_person),
            TutorialStep("阅读与设置", "阅读中心整合历史、稍后看和阅读统计。设置在页面下方，这些教程也能在“设置 → 使用教程”里重新查看。", R.drawable.il_settings),
        )
        TutorialTopic.SEARCH -> listOf(
            TutorialStep("搜索与历史", "输入关键词搜索帖子，点击历史词可再次搜索。向右滑动页面，可返回上一页。", R.drawable.il_search),
        )
        TutorialTopic.IMAGES -> listOf(
            TutorialStep("双击或双指缩放", "双击图片放大，再次双击可继续放大或还原；双指张开、合拢可自由缩放。放大后拖动查看不同位置。", R.drawable.il_zoom),
            TutorialStep("切图与退出", "图片还原后，左右滑动切换图片，向上或向下拖动退出。加载时也能拖动退出；返回键会先还原放大，再退出。", R.drawable.il_swipe),
            TutorialStep("长图与清晰度", "长图可上下滚动阅读，不必先加载整张原图。需要更高清内容时，再点原图按钮；省流模式下先点占位框才加载内容图片。", R.drawable.il_image),
        )
        TutorialTopic.CHECKIN -> listOf(
            TutorialStep("先连接签到服务", "这里的注册和登录使用你的签到服务账号，不是小黑盒账号。没有服务账号时，选择“注册签到服务账号”。", R.drawable.il_person),
            TutorialStep("再登录小黑盒", "服务连接成功后，按会员服务页面提示完成开通（如需要），再选择“手机号登录小黑盒”。验证码或密码属于需要签到的小黑盒账号。", R.drawable.il_calendar),
            TutorialStep("设置后交给服务器", "连接小黑盒后，在签到设置里开启自动签到、设置时间和随机偏移。签到记录可查看最近执行结果，退出 Lite 登录不会自动撤销签到服务。", R.drawable.il_settings),
        )
        TutorialTopic.SERVICE_LOGIN -> listOf(
            TutorialStep("登录的是签到服务", "请填写你在签到网站注册的服务用户名和服务密码。这里不是小黑盒登录，不要填写小黑盒密码。没有服务账号请切换到注册。", R.drawable.il_person),
            TutorialStep("连接后还有一步", "服务账号验证通过后，再回签到首页，选择“手机号登录小黑盒”，用需要签到的小黑盒账号完成绑定。", R.drawable.il_calendar),
        )
        TutorialTopic.SERVICE_REGISTER -> listOf(
            TutorialStep("创建签到服务账号", "这里会创建一个新的签到服务账号，与小黑盒账号相互独立。设置服务用户名和密码；需要邮箱验证时，填写自己的邮箱并获取验证码。", R.drawable.il_person),
            TutorialStep("注册后登录小黑盒", "注册并连接成功后，还要使用“手机号登录小黑盒”绑定签到账号。Lite 扫码登录的浏览凭据不能代替这一步。", R.drawable.il_calendar),
        )
        TutorialTopic.HEYBOX_LOGIN -> listOf(
            TutorialStep("这一步才是小黑盒登录", "填写需要自动签到的小黑盒手机号，选择短信验证码或小黑盒密码登录。这里不要填写签到服务的用户名或密码。", R.drawable.il_person),
            TutorialStep("完成后检查签到设置", "如遇安全验证，请先完成验证再继续。登录连接成功后，检查签到开关、执行时间与最近结果；教程不会代你登录或执行签到。", R.drawable.il_settings),
        )
    }
}
