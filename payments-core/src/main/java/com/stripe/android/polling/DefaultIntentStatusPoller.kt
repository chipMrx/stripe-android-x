package com.stripe.android.polling

import androidx.annotation.RestrictTo
import com.stripe.android.PaymentConfiguration
import com.stripe.android.core.Logger
import com.stripe.android.core.networking.ApiRequest
import com.stripe.android.model.StripeIntent
import com.stripe.android.networking.StripeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.pow

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DefaultIntentStatusPoller @Inject constructor(
    private val stripeRepository: StripeRepository,
    private val paymentConfigProvider: Provider<PaymentConfiguration>,
    private val config: IntentStatusPollingConfig,
    private val dispatcher: CoroutineDispatcher,
    private val logger: Logger
) : IntentStatusPoller {

    private val _state = MutableStateFlow<StripeIntent.Status?>(null)
    override val state: StateFlow<StripeIntent.Status?> = _state

    private val pollingSuspender = PollingSuspender(logger)
    private var pollingJob: Job? = null

    override fun startPolling(scope: CoroutineScope) {
        pollingJob = scope.launch(dispatcher) {
            performPolling()
        }
    }

    private suspend fun performPolling() {
        var attemptedRetries = 1
        while (attemptedRetries <= config.maxRetries) {
            val delayInMillis = calculateDelayInMillis(attemptedRetries)
            logger.debug("Delay by $delayInMillis ms")
            delay(delayInMillis)

            pollingSuspender.waitIfSuspended()

            logger.debug("Polling intent status (attempt #$attemptedRetries)")
            val intentStatus = fetchIntentStatus()
            _state.value = intentStatus

            if (intentStatus != StripeIntent.Status.RequiresAction) {
                // We can stop
                cancel()
                break
            }

            attemptedRetries += 1
        }
    }

    private suspend fun fetchIntentStatus(): StripeIntent.Status? {
        val paymentConfig = paymentConfigProvider.get()
        val paymentIntent = runCatching {
            stripeRepository.retrievePaymentIntent(
                clientSecret = config.clientSecret,
                options = ApiRequest.Options(
                    publishableKeyProvider = { paymentConfig.publishableKey },
                    stripeAccountIdProvider = { paymentConfig.stripeAccountId }
                )
            )
        }
        return paymentIntent.getOrNull()?.status
    }

    override suspend fun forcePoll(): StripeIntent.Status? {
        return fetchIntentStatus()
    }

    override fun resumePolling() {
        pollingSuspender.resume()
    }

    override fun pausePolling() {
        pollingSuspender.suspend()
    }

    override fun cancel() {
        pollingJob?.cancel()
        pollingJob = null
    }
}

private class PollingSuspender(
    private val logger: Logger
) {
    private var channel: Channel<Unit>? = null

    suspend fun waitIfSuspended() {
        if (channel != null) {
            logger.debug("Waiting to resume polling")
            channel?.receive()
            channel = null
        }
    }

    fun resume() {
        logger.debug("Resuming polling")
        channel?.trySend(Unit)
    }

    fun suspend() {
        logger.debug("Suspending polling")
        channel = Channel(0)
    }
}

private fun calculateDelayInMillis(attempt: Int): Long {
    val seconds = (1.0 + attempt).pow(2)
    return seconds.toLong() * 1_000
}
