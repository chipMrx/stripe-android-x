package com.stripe.android.connections

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.stripe.android.connections.ConnectionsSheetViewEffect.FinishWithResult
import com.stripe.android.connections.ConnectionsSheetViewEffect.OpenAuthFlowWithIntent
import com.stripe.android.connections.databinding.ActivityConnectionsSheetBinding
import java.security.InvalidParameterException

internal class ConnectionsSheetActivity : AppCompatActivity() {

    @VisibleForTesting
    internal val viewBinding by lazy {
        ActivityConnectionsSheetBinding.inflate(layoutInflater)
    }

    @VisibleForTesting
    internal var viewModelFactory: ViewModelProvider.Factory =
        ConnectionsSheetViewModel.Factory(
            { application },
            { requireNotNull(starterArgs) },
            this,
            intent?.extras
        )

    private val viewModel: ConnectionsSheetViewModel by viewModels { viewModelFactory }

    private val starterArgs: ConnectionsSheetContract.Args? by lazy {
        ConnectionsSheetContract.Args.fromIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        val starterArgs = this.starterArgs
        if (starterArgs == null) {
            finishWithResult(
                ConnectionsSheetResult.Failed(
                    IllegalArgumentException("ConnectionsSheet started without arguments.")
                )
            )
            return
        } else {
            try {
                starterArgs.validate()
            } catch (e: InvalidParameterException) {
                finishWithResult(ConnectionsSheetResult.Failed(e))
                return
            }
        }

        setupObservers()
    }

    private fun setupObservers() {
        lifecycleScope.launchWhenStarted {
            viewModel.state.collect {
                // process state updates here.
            }
        }
        lifecycleScope.launchWhenStarted {
            viewModel.viewEffect.collect { viewEffect ->
                when (viewEffect) {
                    is OpenAuthFlowWithIntent -> startActivity(viewEffect.intent)
                    is FinishWithResult -> finishWithResult(viewEffect.result)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    /**
     * Handles new intents in the form of the redirect from the custom tab hosted auth flow
     */
    public override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        viewModel.handleOnNewIntent(intent)
    }

    /**
     * If the back button is pressed during the manifest fetch or link account session fetch
     * return canceled result
     */
    override fun onBackPressed() {
        finishWithResult(ConnectionsSheetResult.Canceled)
    }

    private fun finishWithResult(result: ConnectionsSheetResult) {
        setResult(
            Activity.RESULT_OK,
            Intent().putExtras(ConnectionsSheetContract.Result(result).toBundle())
        )
        finish()
    }
}