package com.stripe.android.polling

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class IntentStatusPollingConfig(
    val clientSecret: String,
    val maxRetries: Int
)
