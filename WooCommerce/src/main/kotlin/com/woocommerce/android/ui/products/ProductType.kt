package com.woocommerce.android.ui.products

import android.os.Parcelable
import androidx.annotation.StringRes
import com.woocommerce.android.R
import com.woocommerce.android.ui.products.ProductType.*
import kotlinx.parcelize.Parcelize
import org.wordpress.android.fluxc.network.rest.wpcom.wc.product.CoreProductType
import java.util.Locale

sealed class ProductType(@StringRes val stringResource: Int = 0) : Parcelable {
    @Parcelize
    object SIMPLE : ProductType(R.string.product_type_simple)
    @Parcelize
    object VIRTUAL : ProductType(R.string.product_type_virtual)
    @Parcelize
    object GROUPED : ProductType(R.string.product_type_grouped)
    @Parcelize
    object EXTERNAL : ProductType(R.string.product_type_external)
    @Parcelize
    object VARIABLE : ProductType(R.string.product_type_variable)
    @Parcelize
    class OTHER(val value: String) : ProductType()

    companion object {
        fun fromString(type: String): ProductType {
            return when (type.toLowerCase(Locale.US)) {
                "grouped" -> GROUPED
                "external" -> EXTERNAL
                "variable" -> VARIABLE
                "simple" -> SIMPLE
                "virtual" -> VIRTUAL
                else -> OTHER(type)
            }
        }

        fun fromCoreType(coreProductType: String, isVirtual: Boolean): ProductType {
            return when (CoreProductType.fromValue(coreProductType)) {
                CoreProductType.SIMPLE -> {
                    if (isVirtual) VIRTUAL else SIMPLE
                }
                CoreProductType.GROUPED -> GROUPED
                CoreProductType.EXTERNAL -> EXTERNAL
                CoreProductType.VARIABLE -> VARIABLE
                null -> OTHER(coreProductType)
            }
        }
    }
}

val ProductType.value
    get() = when (this) {
        EXTERNAL -> "external"
        GROUPED -> "grouped"
        is OTHER -> (this as OTHER).value
        SIMPLE -> "simple"
        VARIABLE -> "variable"
        VIRTUAL -> "virtual"
    }
