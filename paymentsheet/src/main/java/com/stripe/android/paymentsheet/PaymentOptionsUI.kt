package com.stripe.android.paymentsheet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import com.stripe.android.model.PaymentMethod
import com.stripe.android.paymentsheet.model.PaymentSelection
import com.stripe.android.paymentsheet.model.SavedSelection
import com.stripe.android.paymentsheet.ui.getLabel
import com.stripe.android.paymentsheet.ui.getLabelIcon
import com.stripe.android.paymentsheet.ui.getSavedPaymentMethodIcon
import com.stripe.android.paymentsheet.viewmodels.BaseSheetViewModel
import com.stripe.android.ui.core.PaymentsTheme
import com.stripe.android.ui.core.paymentsColors
import com.stripe.android.ui.core.shouldUseDarkDynamicColor

private object Spacing {
    val carouselContentPadding = 17.dp
    val itemSpacing = 6.dp
    val minItemWidth = 100.dp + (itemSpacing * 2)
}

@Composable
internal fun rememberPaymentOptions(
    paymentMethods: List<PaymentMethod>,
    isGooglePayEnabled: Boolean,
    isLinkEnabled: Boolean,
    initialSelection: SavedSelection
): List<PaymentOptionsAdapter.Item> {
    return remember(paymentMethods, isGooglePayEnabled, isLinkEnabled) {
        buildList {
            add(PaymentOptionsAdapter.Item.AddCard)

            if (isGooglePayEnabled) {
                add(PaymentOptionsAdapter.Item.GooglePay)
            }

            if (isLinkEnabled) {
                add(PaymentOptionsAdapter.Item.Link)
            }

            for (paymentMethod in sortedPaymentMethods(paymentMethods, initialSelection)) {
                add(PaymentOptionsAdapter.Item.SavedPaymentMethod(paymentMethod))
            }
        }
    }
}

@Composable
internal fun PaymentOptions(
    viewModel: BaseSheetViewModel<*>,
    initialSelection: SavedSelection,
    canClickSelectedItem: Boolean,
    onAddCardPressed: () -> Unit
) {
    val resources = LocalContext.current
    val paymentMethods by viewModel.paymentMethods.observeAsState(initial = emptyList())
    val isGooglePayEnabled by viewModel.isGooglePayReady.observeAsState(initial = false)
    val isLinkEnabled by viewModel.isLinkEnabled.observeAsState(initial = false)
    val selection by viewModel.selection.observeAsState()
    val isEditing by viewModel.editing.collectAsState()

    val items = rememberPaymentOptions(
        paymentMethods = paymentMethods,
        isGooglePayEnabled = viewModel is PaymentOptionsViewModel && isGooglePayEnabled,
        isLinkEnabled = viewModel is PaymentOptionsViewModel && isLinkEnabled,
        initialSelection = initialSelection
    )

    // TODO: What about deletion?
    val selectedItemIndex = selection?.let { items.findSelectedPosition(it) }
        ?: items.findInitialSelectedPosition(initialSelection)

    PaymentOptions(
        items = items,
        selectedItemIndex = selectedItemIndex,
        isEditing = isEditing,
        canClickSelectedItem = canClickSelectedItem,
        paymentMethodNameProvider = { code ->
            viewModel.lpmResourceRepository.getRepository().fromCode(code)?.let {
                resources.getString(it.displayNameResource)
            }
        },
        onAddCardPressed = onAddCardPressed,
        onUpdateSelection = { paymentSelection, isUserSelection ->
            viewModel.updateSelection(paymentSelection)

            if (viewModel is PaymentOptionsViewModel && isUserSelection) {
                viewModel.onUserSelection()
            }
        },
        onRemovePaymentMethod = viewModel::removePaymentMethod
    )
}

@Composable
internal fun PaymentOptions(
    items: List<PaymentOptionsAdapter.Item>,
    selectedItemIndex: Int,
    isEditing: Boolean,
    paymentMethodNameProvider: (String?) -> String?,
    canClickSelectedItem: Boolean,
    onAddCardPressed: () -> Unit,
    onUpdateSelection: (PaymentSelection?, Boolean) -> Unit,
    onRemovePaymentMethod: (PaymentMethod) -> Unit
) {
    // A workaround we have to do for now, until we refactor the view model to expose a single UI
    // state instead of various observable properties that influence each other: Once we have loaded
    // the payment methods, we must notify the view model about the currently selected item.
    if (items.isNotEmpty()) {
        LaunchedEffect(Unit) {
            if (selectedItemIndex in items.indices) {
                val initialPaymentSelection = items[selectedItemIndex].toPaymentSelection()
                onUpdateSelection(initialPaymentSelection, false)
            }
        }
    }

    val handleSelection: (PaymentOptionsAdapter.Item) -> Unit = remember {
        { item ->
            val isAlreadySelected = items.indexOf(item) == selectedItemIndex
            val canSelectItem = !isAlreadySelected || canClickSelectedItem

            if (!isEditing && canSelectItem) {
                val paymentSelection = item.toPaymentSelection()
                onUpdateSelection(paymentSelection, true)
            }
        }
    }

    val itemWidth = rememberItemWidth()

    PaymentsTheme {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.carouselContentPadding)
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedItemIndex

                when (item) {
                    is PaymentOptionsAdapter.Item.AddCard -> {
                        AddCard(
                            width = itemWidth,
                            isEnabled = !isEditing,
                            onSelected = onAddCardPressed
                        )
                    }
                    is PaymentOptionsAdapter.Item.GooglePay -> {
                        GooglePay(
                            width = itemWidth,
                            isSelected = isSelected,
                            isEnabled = !isEditing,
                            onSelected = { handleSelection(item) }
                        )
                    }
                    is PaymentOptionsAdapter.Item.Link -> {
                        Link(
                            width = itemWidth,
                            isSelected = isSelected,
                            isEnabled = !isEditing,
                            onSelected = { handleSelection(item) }
                        )
                    }
                    is PaymentOptionsAdapter.Item.SavedPaymentMethod -> {
                        SavedPaymentMethod(
                            savedPaymentMethod = item,
                            width = itemWidth,
                            isEditing = isEditing,
                            isSelected = isSelected,
                            isEnabled = true,
                            paymentMethodNameProvider = paymentMethodNameProvider,
                            onSelected = { handleSelection(item) },
                            onRemoved = { onRemovePaymentMethod(item.paymentMethod) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberItemWidth(): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    return remember(screenWidth) {
        val contentWidth = screenWidth - (Spacing.carouselContentPadding * 2)
        val numVisibleItems = (contentWidth * 2 / Spacing.minItemWidth).toInt() / 2f
        contentWidth / numVisibleItems
    }
}

@Composable
private fun AddCard(
    width: Dp,
    isEnabled: Boolean,
    onSelected: () -> Unit
) {
    val iconRes = if (MaterialTheme.paymentsColors.component.shouldUseDarkDynamicColor()) {
        R.drawable.stripe_ic_paymentsheet_add_dark
    } else {
        R.drawable.stripe_ic_paymentsheet_add_light
    }

    PaymentOptionUi(
        viewWidth = width,
        isEditing = false,
        isSelected = false,
        isEnabled = isEnabled,
        labelText = stringResource(R.string.stripe_paymentsheet_add_payment_method_button_label),
        iconRes = iconRes,
        onItemSelectedListener = onSelected,
        description = stringResource(R.string.add_new_payment_method)
    )
}

@Composable
private fun GooglePay(
    width: Dp,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSelected: () -> Unit
) {
    PaymentOptionUi(
        viewWidth = width,
        isEditing = false,
        isSelected = isSelected,
        isEnabled = isEnabled,
        iconRes = R.drawable.stripe_google_pay_mark,
        labelText = stringResource(R.string.google_pay),
        description = stringResource(R.string.google_pay),
        onItemSelectedListener = onSelected
    )
}

@Composable
private fun Link(
    width: Dp,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSelected: () -> Unit
) {
    PaymentOptionUi(
        viewWidth = width,
        isEditing = false,
        isSelected = isSelected,
        isEnabled = isEnabled,
        iconRes = R.drawable.stripe_link_mark,
        labelText = stringResource(R.string.link),
        description = stringResource(R.string.link),
        onItemSelectedListener = onSelected
    )
}

@Composable
private fun SavedPaymentMethod(
    savedPaymentMethod: PaymentOptionsAdapter.Item.SavedPaymentMethod,
    width: Dp,
    isEditing: Boolean,
    isSelected: Boolean,
    isEnabled: Boolean,
    paymentMethodNameProvider: (String?) -> String?,
    onSelected: () -> Unit,
    onRemoved: () -> Unit
) {
    val resources = LocalContext.current.resources
    val labelIcon = savedPaymentMethod.paymentMethod.getLabelIcon()
    val labelText = savedPaymentMethod.paymentMethod.getLabel(resources) ?: return

    val paymentMethodName = paymentMethodNameProvider(savedPaymentMethod.paymentMethod.type?.code)
    val removeTitle = resources.getString(R.string.stripe_paymentsheet_remove_pm, paymentMethodName)

    PaymentOptionUi(
        viewWidth = width,
        isEditing = isEditing,
        isSelected = isSelected,
        isEnabled = isEnabled,
        iconRes = savedPaymentMethod.paymentMethod.getSavedPaymentMethodIcon() ?: 0,
        labelIcon = labelIcon,
        labelText = labelText,
        removePmDialogTitle = removeTitle,
        description = savedPaymentMethod.getDescription(resources),
        onRemoveListener = onRemoved,
        onRemoveAccessibilityDescription = savedPaymentMethod.getRemoveDescription(resources),
        onItemSelectedListener = onSelected
    )
}

private fun sortedPaymentMethods(
    paymentMethods: List<PaymentMethod>,
    savedSelection: SavedSelection
): List<PaymentMethod> {
    val primaryPaymentMethodIndex = when (savedSelection) {
        is SavedSelection.PaymentMethod -> {
            paymentMethods.indexOfFirst {
                it.id == savedSelection.id
            }
        }
        else -> -1
    }
    return if (primaryPaymentMethodIndex != -1) {
        val mutablePaymentMethods = paymentMethods.toMutableList()
        mutablePaymentMethods.removeAt(primaryPaymentMethodIndex)
            .also { primaryPaymentMethod ->
                mutablePaymentMethods.add(0, primaryPaymentMethod)
            }
        mutablePaymentMethods
    } else {
        paymentMethods
    }
}

private fun PaymentOptionsAdapter.Item.toPaymentSelection(): PaymentSelection? {
    return when (this) {
        PaymentOptionsAdapter.Item.AddCard -> {
            null
        }
        PaymentOptionsAdapter.Item.GooglePay -> {
            PaymentSelection.GooglePay
        }
        PaymentOptionsAdapter.Item.Link -> {
            PaymentSelection.Link
        }
        is PaymentOptionsAdapter.Item.SavedPaymentMethod -> {
            PaymentSelection.Saved(paymentMethod)
        }
    }
}

private fun List<PaymentOptionsAdapter.Item>.findSelectedPosition(
    paymentSelection: PaymentSelection
): Int {
    return indexOfFirst { item ->
        when (paymentSelection) {
            PaymentSelection.GooglePay -> item is PaymentOptionsAdapter.Item.GooglePay
            PaymentSelection.Link -> item is PaymentOptionsAdapter.Item.Link
            is PaymentSelection.Saved -> {
                when (item) {
                    is PaymentOptionsAdapter.Item.SavedPaymentMethod -> {
                        paymentSelection.paymentMethod.id == item.paymentMethod.id
                    }
                    else -> false
                }
            }
            else -> false
        }
    }
}

private fun List<PaymentOptionsAdapter.Item>.findInitialSelectedPosition(
    savedSelection: SavedSelection?
): Int {
    return listOfNotNull(
        // saved selection
        indexOfFirst { item ->
            val b = when (savedSelection) {
                SavedSelection.GooglePay -> item is PaymentOptionsAdapter.Item.GooglePay
                SavedSelection.Link -> item is PaymentOptionsAdapter.Item.Link
                is SavedSelection.PaymentMethod -> {
                    when (item) {
                        is PaymentOptionsAdapter.Item.SavedPaymentMethod -> {
                            savedSelection.id == item.paymentMethod.id
                        }
                        else -> false
                    }
                }
                SavedSelection.None -> false
                else -> false
            }
            b
        }.takeIf { it != -1 },

        // Google Pay
        indexOfFirst { it is PaymentOptionsAdapter.Item.GooglePay }.takeIf { it != -1 },

        // Link
        indexOfFirst { it is PaymentOptionsAdapter.Item.Link }.takeIf { it != -1 },

        // the first payment method
        indexOfFirst { it is PaymentOptionsAdapter.Item.SavedPaymentMethod }.takeIf { it != -1 }
    ).firstOrNull() ?: RecyclerView.NO_POSITION
}
