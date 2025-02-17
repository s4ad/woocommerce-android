package com.woocommerce.android.ui.inbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.woocommerce.android.R
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.viewmodel.ScopedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val resourceProvider: ResourceProvider,
    savedState: SavedStateHandle,
) : ScopedViewModel(savedState) {
    val inboxState = loadInboxNotes().asLiveData()

    @Suppress("MagicNumber", "LongMethod")
    private fun loadInboxNotes(): Flow<InboxState> = flow {
        emit(InboxState(isLoading = true))
        delay(2000)
        emit(InboxState(isLoading = false, notes = emptyList()))
        delay(2000)
        emit(
            InboxState(
                notes = listOf(
                    InboxNoteUi(
                        id = "1",
                        title = "Install the Facebook free extension",
                        description = "Now that your store is set up, you’re ready to begin marketing it. " +
                            "Head over to the WooCommerce marketing panel to get started.",
                        updatedTime = "5h ago",
                        callToActionText = "Learn more",
                        onCallToActionClick = {},
                        dismissText = resourceProvider.getString(R.string.dismiss_inbox_note),
                        onDismissNote = {},
                        isRead = false
                    ),
                    InboxNoteUi(
                        id = "2",
                        title = "Connect with your audience",
                        description = "Grow your customer base and increase your sales with marketing tools " +
                            "built for WooCommerce.",
                        updatedTime = "22 minutes ago",
                        callToActionText = "Learn more",
                        onCallToActionClick = {},
                        dismissText = resourceProvider.getString(R.string.dismiss_inbox_note),
                        onDismissNote = {},
                        isRead = false
                    ),
                    InboxNoteUi(
                        id = "1",
                        title = "Install the Facebook free extension",
                        description = "Now that your store is set up, you’re ready to begin marketing it. " +
                            "Head over to the WooCommerce marketing panel to get started.",
                        updatedTime = "5h ago",
                        callToActionText = "Learn more",
                        onCallToActionClick = {},
                        dismissText = resourceProvider.getString(R.string.dismiss_inbox_note),
                        onDismissNote = {},
                        isRead = false
                    ),
                    InboxNoteUi(
                        id = "1",
                        title = "Install the Facebook free extension",
                        description = "Now that your store is set up, you’re ready to begin marketing it. " +
                            "Head over to the WooCommerce marketing panel to get started.",
                        updatedTime = "5h ago",
                        callToActionText = "Learn more",
                        onCallToActionClick = {},
                        dismissText = resourceProvider.getString(R.string.dismiss_inbox_note),
                        onDismissNote = {},
                        isRead = false
                    )
                )
            )
        )
    }

    data class InboxState(
        val isLoading: Boolean = false,
        val notes: List<InboxNoteUi> = emptyList()
    )

    data class InboxNoteUi(
        val id: String,
        val title: String,
        val description: String,
        val updatedTime: String,
        val callToActionText: String,
        val onCallToActionClick: (String) -> Unit,
        val dismissText: String,
        val onDismissNote: (String) -> Unit,
        val isRead: Boolean
    )
}
