package net.mkhalil.taktik.ui.nav

object Routes {
    const val CONNECT = "connect"
    const val LIVE = "live"                    // current/active session
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    const val SESSION_DETAIL = "session/{sessionId}"
    fun sessionDetail(sessionId: Long) = "session/$sessionId"

    const val VIEWER_DETAIL = "viewer/{userId}"
    fun viewerDetail(userId: String) = "viewer/$userId"
}
