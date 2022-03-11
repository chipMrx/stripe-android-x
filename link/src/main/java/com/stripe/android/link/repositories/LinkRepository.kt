package com.stripe.android.link.repositories

import com.stripe.android.model.ConsumerPaymentDetails
import com.stripe.android.model.ConsumerSession
import com.stripe.android.model.ConsumerSessionLookup

/**
 * Interface for a repository that interacts with Link services.
 */
internal interface LinkRepository {

    /**
     * Check if the email already has a link account.
     */
    suspend fun lookupConsumer(
        email: String
    ): Result<ConsumerSessionLookup>

    /**
     * Sign up for a new Link account.
     */
    suspend fun consumerSignUp(
        email: String,
        phone: String,
        country: String
    ): Result<ConsumerSession>

    /**
     * Start an SMS verification.
     */
    suspend fun startVerification(
        consumerSessionClientSecret: String
    ): Result<ConsumerSession>

    /**
     * Confirm an SMS verification code.
     */
    suspend fun confirmVerification(
        consumerSessionClientSecret: String,
        verificationCode: String
    ): Result<ConsumerSession>

    /**
     * Fetch all saved payment methods for the consumer.
     */
    suspend fun listPaymentDetails(
        consumerSessionClientSecret: String
    ): Result<ConsumerPaymentDetails>
}