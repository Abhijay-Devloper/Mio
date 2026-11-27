package com.example.core.feedback

object ActiveChatTracker {
  @Volatile
  var currentChatId: String? = null

  @Volatile
  var isAppInForeground: Boolean = false

  fun isActivelyViewingChat(chatId: String?): Boolean {
    return isAppInForeground && !chatId.isNullOrBlank() && currentChatId == chatId
  }
}
