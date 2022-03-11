package com.stripe.android.ui.core.elements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

@Composable
internal fun AddressElementUI(
    enabled: Boolean,
    controller: AddressController
) {
    val fields by controller.fieldsFlowable.collectAsState(null)
    fields?.let { fieldList ->
        Column {
            fieldList.forEachIndexed { index, field ->
                SectionFieldElementUI(enabled, field)
                if (index != fieldList.size - 1) {
                    Divider(
                        color = CardStyle.cardDividerColor,
                        thickness = CardStyle.cardBorderWidth,
                        modifier = Modifier.padding(
                            horizontal = CardStyle.cardBorderWidth
                        )
                    )
                }
            }
        }
    }
}