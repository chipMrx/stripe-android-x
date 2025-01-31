package com.stripe.android.identity.navigation

import android.content.Context
import android.view.View
import androidx.annotation.IdRes
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.stripe.android.identity.IdentityVerificationSheet
import com.stripe.android.identity.IdentityVerificationSheet.VerificationFlowResult.Failed
import com.stripe.android.identity.R
import com.stripe.android.identity.VerificationFlowFinishable
import com.stripe.android.identity.analytics.IdentityAnalyticsRequestFactory.Companion.SCREEN_NAME_ERROR
import com.stripe.android.identity.networking.models.VerificationPageDataRequirementError
import com.stripe.android.identity.networking.models.VerificationPageDataRequirementError.Requirement.Companion.matchesFromFragment
import com.stripe.android.identity.utils.navigateUpAndSetArgForUploadFragment

/**
 * Fragment to show generic error.
 */
internal class ErrorFragment(
    private val verificationFlowFinishable: VerificationFlowFinishable,
    identityViewModelFactory: ViewModelProvider.Factory
) : BaseErrorFragment(identityViewModelFactory) {
    override fun onCustomizingViews() {
        val args = requireNotNull(arguments)
        title.text = args[ARG_ERROR_TITLE] as String
        message1.text = args[ARG_ERROR_CONTENT] as String
        message2.visibility = View.GONE

        topButton.visibility = View.GONE

        val cause = requireNotNull(args.getSerializable(ARG_CAUSE) as? Throwable) {
            "cause of error is null"
        }

        identityViewModel.sendAnalyticsRequest(
            identityViewModel.identityAnalyticsRequestFactory.genericError(
                message = cause.message,
                stackTrace = cause.stackTraceToString()
            )
        )

        bottomButton.text = args[ARG_GO_BACK_BUTTON_TEXT] as String
        bottomButton.visibility = View.VISIBLE

        // If ARG_SHOULD_FAIL is true, clicking bottom button and pressBack would end flow with Failed
        if (args.getBoolean(ARG_SHOULD_FAIL, false)) {
            identityViewModel.screenTracker.screenTransitionStart(
                SCREEN_NAME_ERROR
            )
            bottomButton.setOnClickListener {
                verificationFlowFinishable.finishWithResult(
                    Failed(cause)
                )
            }
        } else {
            bottomButton.setOnClickListener {
                identityViewModel.screenTracker.screenTransitionStart(
                    SCREEN_NAME_ERROR
                )
                val destination = args[ARG_GO_BACK_BUTTON_DESTINATION] as Int
                if (destination == UNEXPECTED_DESTINATION) {
                    findNavController().navigate(DEFAULT_BACK_BUTTON_NAVIGATION)
                } else {
                    findNavController().let { navController ->
                        var shouldContinueNavigateUp = true
                        while (shouldContinueNavigateUp && navController.currentDestination?.id != destination) {
                            shouldContinueNavigateUp =
                                navController.navigateUpAndSetArgForUploadFragment()
                        }
                    }
                }
            }
        }
    }

    internal companion object {
        const val ARG_ERROR_TITLE = "errorTitle"
        const val ARG_ERROR_CONTENT = "errorContent"

        // if set, shows go_back button, clicking it would navigate to the destination.
        const val ARG_GO_BACK_BUTTON_TEXT = "goBackButtonText"
        const val ARG_GO_BACK_BUTTON_DESTINATION = "goBackButtonDestination"

        // if set to true, clicking bottom button and pressBack would end flow with Failed
        const val ARG_SHOULD_FAIL = "shouldFail"
        const val ARG_CAUSE = "cause"

        // Indicates the server returns a requirementError that doesn't match with current Fragment.
        //  E.g ConsentFragment->DocSelectFragment could only have BIOMETRICCONSENT error but not IDDOCUMENTFRONT error.
        // If this happens, set the back button destination to [DEFAULT_BACK_BUTTON_DESTINATION]
        internal const val UNEXPECTED_DESTINATION = -1

        private val DEFAULT_BACK_BUTTON_NAVIGATION =
            R.id.action_errorFragment_to_consentFragment

        fun NavController.navigateToErrorFragmentWithRequirementError(
            @IdRes fromFragment: Int,
            requirementError: VerificationPageDataRequirementError
        ) {
            navigate(
                R.id.action_global_errorFragment,
                bundleOf(
                    ARG_ERROR_TITLE to (
                        requirementError.title ?: context.getString(R.string.error)
                        ),
                    ARG_ERROR_CONTENT to (
                        requirementError.body
                            ?: context.getString(R.string.unexpected_error_try_again)
                        ),
                    ARG_GO_BACK_BUTTON_DESTINATION to
                        if (requirementError.requirement.matchesFromFragment(fromFragment)) {
                            fromFragment
                        } else {
                            UNEXPECTED_DESTINATION
                        },
                    ARG_GO_BACK_BUTTON_TEXT to (
                        requirementError.backButtonText ?: context.getString(R.string.go_back)
                        ),
                    ARG_SHOULD_FAIL to false,
                    ARG_CAUSE to IllegalStateException("VerificationPageDataRequirementError: $requirementError")
                )
            )
        }

        fun NavController.navigateToErrorFragmentWithDefaultValues(
            context: Context,
            cause: Throwable
        ) {
            navigate(
                R.id.action_global_errorFragment,
                bundleOf(
                    ARG_ERROR_TITLE to context.getString(R.string.error),
                    ARG_ERROR_CONTENT to context.getString(R.string.unexpected_error_try_again),
                    ARG_GO_BACK_BUTTON_DESTINATION to R.id.consentFragment,
                    ARG_GO_BACK_BUTTON_TEXT to context.getString(R.string.go_back),
                    ARG_SHOULD_FAIL to false,
                    ARG_CAUSE to cause
                )
            )
        }

        /**
         * Navigate to error fragment with failed reason. This would be the final destination of
         * verification flow, clicking back button would end the follow with
         * [IdentityVerificationSheet.VerificationFlowResult.Failed] with [failedReason].
         */
        fun NavController.navigateToErrorFragmentWithFailedReason(
            context: Context,
            failedReason: Throwable
        ) {
            navigate(
                R.id.action_global_errorFragment,
                bundleOf(
                    ARG_ERROR_TITLE to context.getString(R.string.error),
                    ARG_ERROR_CONTENT to context.getString(R.string.unexpected_error_try_again),
                    ARG_GO_BACK_BUTTON_TEXT to context.getString(R.string.go_back),
                    ARG_SHOULD_FAIL to true,
                    ARG_CAUSE to failedReason
                )
            )
        }
    }
}
