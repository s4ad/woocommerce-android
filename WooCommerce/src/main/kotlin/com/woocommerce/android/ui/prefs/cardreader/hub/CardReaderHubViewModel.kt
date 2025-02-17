package com.woocommerce.android.ui.prefs.cardreader.hub

import androidx.annotation.DrawableRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.AppUrls
import com.woocommerce.android.R
import com.woocommerce.android.model.UiString
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.prefs.cardreader.InPersonPaymentsCanadaFeatureFlag
import com.woocommerce.android.ui.prefs.cardreader.onboarding.PluginType.STRIPE_EXTENSION_GATEWAY
import com.woocommerce.android.ui.prefs.cardreader.onboarding.PluginType.WOOCOMMERCE_PAYMENTS
import com.woocommerce.android.viewmodel.MultiLiveEvent
import com.woocommerce.android.viewmodel.ScopedViewModel
import com.woocommerce.android.viewmodel.navArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.internal.toImmutableList
import javax.inject.Inject

@HiltViewModel
class CardReaderHubViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val inPersonPaymentsCanadaFeatureFlag: InPersonPaymentsCanadaFeatureFlag,
    private val appPrefsWrapper: AppPrefsWrapper,
    private val selectedSite: SelectedSite,
) : ScopedViewModel(savedState) {
    private val arguments: CardReaderHubFragmentArgs by savedState.navArgs()

    private val cardReaderPurchaseUrl: String by lazy {
        if (inPersonPaymentsCanadaFeatureFlag.isEnabled()) {
            // todo fix the URL when decided
            "${AppUrls.WOOCOMMERCE_PURCHASE_CARD_READER_IN_COUNTRY}${arguments.storeCountryCode}"
        } else {
            val preferredPlugin = appPrefsWrapper.getCardReaderPreferredPlugin(
                selectedSite.get().id,
                selectedSite.get().siteId,
                selectedSite.get().selfHostedSiteId
            )
            when (preferredPlugin) {
                STRIPE_EXTENSION_GATEWAY -> AppUrls.STRIPE_M2_PURCHASE_CARD_READER
                WOOCOMMERCE_PAYMENTS, null -> AppUrls.WOOCOMMERCE_M2_PURCHASE_CARD_READER
            }
        }
    }

    private val viewState = MutableLiveData<CardReaderHubViewState>(
        createInitialState()
    )

    private fun createInitialState() = CardReaderHubViewState.Content(
        mutableListOf(
            CardReaderHubListItemViewState(
                icon = R.drawable.ic_shopping_cart,
                label = UiString.UiStringRes(R.string.card_reader_purchase_card_reader),
                onItemClicked = ::onPurchaseCardReaderClicked
            ),
            CardReaderHubListItemViewState(
                icon = R.drawable.ic_manage_card_reader,
                label = UiString.UiStringRes(R.string.card_reader_manage_card_reader),
                onItemClicked = ::onManageCardReaderClicked
            ),
            CardReaderHubListItemViewState(
                icon = R.drawable.ic_card_reader_manual,
                label = UiString.UiStringRes(R.string.card_reader_bbpos_manual_card_reader),
                onItemClicked = ::onBbposManualCardReaderClicked
            ),
            CardReaderHubListItemViewState(
                icon = R.drawable.ic_card_reader_manual,
                label = UiString.UiStringRes(R.string.card_reader_m2_manual_card_reader),
                onItemClicked = ::onM2ManualCardReaderClicked
            )
        ).apply {
            if (inPersonPaymentsCanadaFeatureFlag.isEnabled()) {
                add(
                    CardReaderHubListItemViewState(
                        icon = R.drawable.ic_card_reader_manual,
                        label = UiString.UiStringRes(R.string.card_reader_wisepad_3_manual_card_reader),
                        onItemClicked = ::onWisePad3ManualCardReaderClicked
                    )
                )
            }
        }.toImmutableList()
    )

    val viewStateData: LiveData<CardReaderHubViewState> = viewState

    private fun onManageCardReaderClicked() {
        triggerEvent(CardReaderHubEvents.NavigateToCardReaderDetail)
    }

    private fun onPurchaseCardReaderClicked() {
        triggerEvent(CardReaderHubEvents.NavigateToPurchaseCardReaderFlow(cardReaderPurchaseUrl))
    }

    private fun onBbposManualCardReaderClicked() {
        triggerEvent(CardReaderHubEvents.NavigateToManualCardReaderFlow(AppUrls.BBPOS_MANUAL_CARD_READER))
    }

    private fun onM2ManualCardReaderClicked() {
        triggerEvent(CardReaderHubEvents.NavigateToManualCardReaderFlow(AppUrls.M2_MANUAL_CARD_READER))
    }

    private fun onWisePad3ManualCardReaderClicked() {
        triggerEvent(CardReaderHubEvents.NavigateToManualCardReaderFlow(AppUrls.WISEPAD_3_MANUAL_CARD_READER))
    }

    sealed class CardReaderHubEvents : MultiLiveEvent.Event() {
        object NavigateToCardReaderDetail : CardReaderHubEvents()
        data class NavigateToPurchaseCardReaderFlow(val url: String) : CardReaderHubEvents()
        data class NavigateToManualCardReaderFlow(val url: String) : CardReaderHubEvents()
    }

    sealed class CardReaderHubViewState {
        abstract val rows: List<CardReaderHubListItemViewState>

        data class Content(override val rows: List<CardReaderHubListItemViewState>) : CardReaderHubViewState()
    }

    data class CardReaderHubListItemViewState(
        @DrawableRes val icon: Int,
        val label: UiString,
        val onItemClicked: () -> Unit
    )
}
