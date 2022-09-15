package com.stripe.android.paymentsheet

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import com.stripe.android.paymentsheet.databinding.FragmentPaymentsheetPaymentMethodsListBinding
import com.stripe.android.paymentsheet.model.FragmentConfig
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.ui.BaseSheetActivity
import com.stripe.android.paymentsheet.viewmodels.BaseSheetViewModel
import com.stripe.android.ui.core.PaymentsThemeDefaults
import com.stripe.android.ui.core.createTextSpanFromTextStyle
import com.stripe.android.ui.core.isSystemDarkTheme

internal abstract class BasePaymentMethodsListFragment(
    private val canClickSelectedItem: Boolean
) : Fragment(
    R.layout.fragment_paymentsheet_payment_methods_list
) {

    abstract val sheetViewModel: BaseSheetViewModel<*>

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.paymentsheet_payment_methods_list, menu).also {
                editMenuItem = menu.findItem(R.id.edit)
            }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.edit -> {
                    sheetViewModel.editing.value = !sheetViewModel.editing.value
                    true
                }
                else -> {
                    true
                }
            }
        }
    }

    protected lateinit var config: FragmentConfig
    private lateinit var adapter: PaymentOptionsAdapter
    private var editMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nullableConfig = arguments?.getParcelable<FragmentConfig>(
            BaseSheetActivity.EXTRA_FRAGMENT_CONFIG
        )
        if (nullableConfig == null) {
            sheetViewModel.onFatal(
                IllegalArgumentException("Failed to start existing payment options fragment.")
            )
            return
        }
        this.config = nullableConfig

        sheetViewModel.showEditMenu.observe(this) { showEditMenu ->
            if (showEditMenu) {
                requireActivity().addMenuProvider(menuProvider)
            } else {
                requireActivity().removeMenuProvider(menuProvider)
            }
        }

        sheetViewModel.editing.asLiveData().observe(this) { isEditing ->
            setEditMenuText(isEditing)
        }

        sheetViewModel.eventReporter.onShowExistingPaymentOptions(
            linkEnabled = sheetViewModel.isLinkEnabled.value ?: false,
            activeLinkSession = sheetViewModel.activeLinkSession.value ?: false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewBinding = FragmentPaymentsheetPaymentMethodsListBinding.bind(view)
        viewBinding.composeView.setContent {
            PaymentOptions(
                viewModel = sheetViewModel,
                initialSelection = config.savedSelection,
                canClickSelectedItem = canClickSelectedItem,
                onAddCardPressed = this::transitionToAddPaymentMethod
            )
        }
    }

    override fun onResume() {
        super.onResume()

        sheetViewModel.headerText.value =
            getString(R.string.stripe_paymentsheet_select_payment_method)
    }

    private fun setEditMenuText(isEditing: Boolean) {
        val context = context ?: return
        val appearance = sheetViewModel.config?.appearance ?: return
        editMenuItem?.apply {
            title = createTextSpanFromTextStyle(
                text = getString(if (isEditing) R.string.done else R.string.edit),
                context = context,
                fontSizeDp = (
                    appearance.typography.sizeScaleFactor
                        * PaymentsThemeDefaults.typography.smallFontSize.value
                    ).dp,
                color = Color(appearance.getColors(context.isSystemDarkTheme()).appBarIcon),
                fontFamily = appearance.typography.fontResId
            )
        }
    }

    abstract fun transitionToAddPaymentMethod()

    open fun onPaymentOptionSelected(
        paymentSelection: PaymentSelection,
        isClick: Boolean
    ) {
        sheetViewModel.updateSelection(paymentSelection)
    }
}
