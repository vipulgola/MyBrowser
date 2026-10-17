package com.personal.mybrowser.browser

import com.personal.mybrowser.network.LoadedHtml
import com.personal.mybrowser.network.HttpsAddress

sealed interface WebPageState {
    data object Local : WebPageState
    data class Loading(val address: HttpsAddress) : WebPageState
    data class Ready(val page: LoadedHtml) : WebPageState
    data class Failed(val address: HttpsAddress, val message: String, val isReload: Boolean = false) : WebPageState
}
