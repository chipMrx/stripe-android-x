package com.stripe.android.polling

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.stripe.android.PaymentConfiguration
import com.stripe.android.core.Logger
import com.stripe.android.core.networking.ApiRequest
import com.stripe.android.model.PaymentIntent
import com.stripe.android.model.StripeIntent
import com.stripe.android.networking.AbsFakeStripeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultIntentStatusPollerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `Handles success on first poll correctly`() = runTest(testDispatcher) {
        val poller = createIntentStatusPoller(
            enqueuedStatuses = listOf(StripeIntent.Status.Succeeded),
            dispatcher = testDispatcher
        )

        poller.startPolling(scope = this@runTest)

        poller.state.test {
            assertThat(awaitItem()).isNull()

            advanceTimeBy(calculateDelayInMillis(attempt = 1))

            assertThat(awaitItem()).isEqualTo(StripeIntent.Status.Succeeded)
        }
    }

    @Test
    fun `Continues polling if status is requires_action`() = runTest(testDispatcher) {
        val poller = createIntentStatusPoller(
            enqueuedStatuses = listOf(
                StripeIntent.Status.RequiresAction,
                StripeIntent.Status.Succeeded
            ),
            dispatcher = testDispatcher
        )

        poller.startPolling(scope = this@runTest)

        poller.state.test {
            assertThat(awaitItem()).isNull()

            advanceTimeBy(calculateDelayInMillis(attempt = 1))

            assertThat(awaitItem()).isEqualTo(StripeIntent.Status.RequiresAction)

            advanceTimeBy(calculateDelayInMillis(attempt = 2))

            assertThat(awaitItem()).isEqualTo(StripeIntent.Status.Succeeded)
        }
    }

    @Test
    fun `Canceling polling makes poller not emit any more states`() = runTest(testDispatcher) {
        val poller = createIntentStatusPoller(
            enqueuedStatuses = listOf(
                StripeIntent.Status.RequiresAction,
                StripeIntent.Status.RequiresAction
            ),
            dispatcher = testDispatcher
        )

        poller.startPolling(scope = this@runTest)

        poller.state.test {
            assertThat(awaitItem()).isNull()

            advanceTimeBy(calculateDelayInMillis(attempt = 1))

            assertThat(awaitItem()).isEqualTo(StripeIntent.Status.RequiresAction)

            poller.cancel()

            advanceTimeBy(calculateDelayInMillis(attempt = 2))

            expectNoEvents()
        }
    }

    @Test
    fun `Pausing makes poller not emit any more states until resumed`() = runTest(testDispatcher) {
        val poller = createIntentStatusPoller(
            enqueuedStatuses = listOf(
                StripeIntent.Status.RequiresAction,
                StripeIntent.Status.Succeeded
            ),
            dispatcher = testDispatcher
        )

        poller.startPolling(scope = this@runTest)

        poller.state.test {
            assertThat(awaitItem()).isNull()

            advanceTimeBy(calculateDelayInMillis(attempt = 1))

            assertThat(awaitItem()).isEqualTo(StripeIntent.Status.RequiresAction)

            poller.pausePolling()

            // Wait extra long until we resume polling
            advanceTimeBy(calculateDelayInMillis(attempt = 3))

            expectNoEvents()

            poller.resumePolling()
            assertThat(awaitItem()).isEqualTo(StripeIntent.Status.Succeeded)
        }
    }
}

private fun createIntentStatusPoller(
    enqueuedStatuses: List<StripeIntent.Status?>,
    dispatcher: CoroutineDispatcher,
    maxRetries: Int = 10
): DefaultIntentStatusPoller {
    return DefaultIntentStatusPoller(
        stripeRepository = FakeStripeRepository(enqueuedStatuses),
        paymentConfigProvider = {
            PaymentConfiguration(
                publishableKey = "key",
                stripeAccountId = "account_id"
            )
        },
        config = IntentStatusPollingConfig(
            clientSecret = "secret",
            maxRetries = maxRetries
        ),
        dispatcher = dispatcher,
        logger = Logger.noop()
    )
}

private class FakeStripeRepository(
    enqueuedStatuses: List<StripeIntent.Status?>
) : AbsFakeStripeRepository() {

    private val queue = enqueuedStatuses.toMutableList()

    override suspend fun retrievePaymentIntent(
        clientSecret: String,
        options: ApiRequest.Options,
        expandFields: List<String>
    ): PaymentIntent {
        val intentStatus = queue.removeFirst()
        return mock {
            on { status } doReturn intentStatus
        }
    }
}
