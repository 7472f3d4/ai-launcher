package com.g7r.ailauncher.data

import android.content.pm.ApplicationInfo

/**
 * Resolves an app to one of [AppCategory]. Priority:
 *  1. User override (learned from manual re-categorization)
 *  2. Android's declared ApplicationInfo.category
 *  3. Package-name heuristics
 *  4. Fallback to ユーティリティ
 *
 * RECENTLY_ADDED is never assigned here; it is an overlay derived from install time.
 */
object Categorizer {

    fun categorize(appInfo: ApplicationInfo, packageName: String, override: String?): AppCategory {
        if (override != null) {
            val resolved = runCatching { AppCategory.valueOf(override) }.getOrNull()
            if (resolved != null && resolved != AppCategory.RECENTLY_ADDED) return resolved
        }
        fromAndroidCategory(appInfo.category)?.let { return it }
        fromPackageName(packageName)?.let { return it }
        return AppCategory.UTILITIES
    }

    private fun fromAndroidCategory(category: Int): AppCategory? = when (category) {
        ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
        ApplicationInfo.CATEGORY_AUDIO -> AppCategory.MUSIC
        ApplicationInfo.CATEGORY_VIDEO -> AppCategory.ENTERTAINMENT
        ApplicationInfo.CATEGORY_IMAGE -> AppCategory.PHOTO_VIDEO
        ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SNS
        ApplicationInfo.CATEGORY_NEWS -> AppCategory.NEWS_READING
        ApplicationInfo.CATEGORY_MAPS -> AppCategory.TRAVEL
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.PRODUCTIVITY_FINANCE
        else -> null
    }

    private fun fromPackageName(pkg: String): AppCategory? {
        val p = pkg.lowercase()
        return when {
            p.hits("instagram", "facebook", "twitter", "discord", "snapchat", "tiktok",
                "whatsapp", "telegram", "messenger", "line", "mixi", "mastodon", "misskey",
                "bluesky", "threads", "reddit", "linkedin", "wechat", "kakao") -> AppCategory.SNS

            p.hits("netflix", "hulu", "disney", "primevideo", "amazonvideo", "abema", "tver",
                "niconico", "twitch", "youtube", "videostreaming", ".tv", "crunchyroll",
                "unext", "dazn") -> AppCategory.ENTERTAINMENT

            p.hits("spotify", "music", "awa", "deezer", "soundcloud", "radio", "podcast",
                "applemusic", "amazonmusic", "linemusic", "shazam") -> AppCategory.MUSIC

            p.hits("camera", "gallery", "photos", "gphotos", "snapseed", "vsco", "filmo",
                "googlecamera", "openCamera") -> AppCategory.PHOTO_VIDEO

            p.hits("bank", "pay", "paypay", "finance", "money", "wallet", "mufg", "mizuho",
                "rakuten.bank", "office", "word", "excel", "powerpoint", "docs", "sheets",
                "drive", "notion", "evernote", "calendar", "gmail", "outlook", "keep",
                "todo", "slack", "zoom", "teams", "trello", "dropbox", "onedrive",
                "moneyforward", "kakeibo") -> AppCategory.PRODUCTIVITY_FINANCE

            p.hits("maps", "navi", "transit", "jorudan", "norikae", "booking", "expedia",
                "airbnb", "trip", "hotel", "uber", "taxi", "navitime", "jal", "ana",
                "railway", "ekispert") -> AppCategory.TRAVEL

            p.hits("amazon", "rakuten.ichiba", "mercari", "shop", "store", "zozo", "aliexpress",
                "qoo10", "kakaku", "yahoo.shopping", "paypaymall", "shein", "temu") -> AppCategory.SHOPPING

            p.hits("tabelog", "gurunavi", "cookpad", "recipe", "home", "smarthome", "dating",
                "pairs", "tinder", "delish", "food") -> AppCategory.LIFESTYLE

            p.hits("health", "fit", "fitbit", "calorie", "sleep", "run", "strava", "medical",
                "hospital", "asken", "pokemongo", "workout") -> AppCategory.HEALTH_FITNESS

            p.hits("duolingo", "study", "edu", "learn", "school", "dictionary", "eigo",
                "manabi", "anki", "kahoot", "benesse", "udemy") -> AppCategory.EDUCATION

            p.hits("news", "smartnews", "newspicks", "kindle", "book", "manga", "comic",
                "reader", "pocket", "feedly", "nhk", "newspaper") -> AppCategory.NEWS_READING

            p.hits("game", "puzzle", "rpg", ".games") -> AppCategory.GAMES

            p.hits("draw", "paint", "ibis", "capcut", "premiere", "canva", "figma", "blender",
                "picsart", "design", "lightroom", "photoshop", "kine") -> AppCategory.CREATIVITY

            p.hits("files", "clock", "calculator", "settings", "weather", "scanner", "vpn",
                "battery", "authenticator", "browser", "chrome", "firefox") -> AppCategory.UTILITIES

            else -> null
        }
    }

    private fun String.hits(vararg keys: String): Boolean = keys.any { this.contains(it) }
}
