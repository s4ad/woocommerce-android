package com.woocommerce.android.ui.products

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import com.automattic.android.tracks.crashlogging.CrashLogging
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import com.woocommerce.android.R
import com.woocommerce.android.RequestCodes
import com.woocommerce.android.analytics.AnalyticsEvent
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.databinding.FragmentProductDetailBinding
import com.woocommerce.android.extensions.*
import com.woocommerce.android.model.Product
import com.woocommerce.android.model.Product.Image
import com.woocommerce.android.ui.aztec.AztecEditorFragment
import com.woocommerce.android.ui.aztec.AztecEditorFragment.Companion.ARG_AZTEC_EDITOR_TEXT
import com.woocommerce.android.ui.dialog.WooDialog
import com.woocommerce.android.ui.products.ProductDetailViewModel.HideImageUploadErrorSnackbar
import com.woocommerce.android.ui.products.ProductDetailViewModel.RefreshMenu
import com.woocommerce.android.ui.products.ProductInventoryViewModel.InventoryData
import com.woocommerce.android.ui.products.ProductNavigationTarget.ViewProductDetailBottomSheet
import com.woocommerce.android.ui.products.ProductPricingViewModel.PricingData
import com.woocommerce.android.ui.products.ProductShippingViewModel.ShippingData
import com.woocommerce.android.ui.products.ProductTypesBottomSheetViewModel.ProductTypesBottomSheetUiItem
import com.woocommerce.android.ui.products.adapters.ProductPropertyCardsAdapter
import com.woocommerce.android.ui.products.models.ProductPropertyCard
import com.woocommerce.android.ui.products.variations.VariationListFragment
import com.woocommerce.android.ui.products.variations.VariationListViewModel.VariationListData
import com.woocommerce.android.util.ChromeCustomTabUtils
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.*
import com.woocommerce.android.widgets.CustomProgressDialog
import com.woocommerce.android.widgets.SkeletonView
import com.woocommerce.android.widgets.WCProductImageGalleryView.OnGalleryImageInteractionListener
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.util.ActivityUtils
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ProductDetailFragment :
    BaseProductFragment(R.layout.fragment_product_detail),
    OnGalleryImageInteractionListener {
    companion object {
        private const val LIST_STATE_KEY = "list_state"

        const val KEY_PRODUCT_DETAIL_RESULT = "product_detail_result"
        const val KEY_PRODUCT_DETAIL_DID_TRASH = "product_detail_did_trash"
        const val KEY_REMOTE_PRODUCT_ID = "remote_product_id"
    }

    private var productName = ""
        set(value) {
            field = value
            updateActivityTitle()
        }

    private var productId: Long = ProductDetailViewModel.DEFAULT_ADD_NEW_PRODUCT_ID

    private val skeletonView = SkeletonView()

    private var progressDialog: CustomProgressDialog? = null
    private var layoutManager: LayoutManager? = null
    private var saveMenuItem: MenuItem? = null
    private var publishMenuItem: MenuItem? = null
    private var imageUploadErrorsSnackbar: Snackbar? = null

    private var _binding: FragmentProductDetailBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var crashLogging: CrashLogging

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transitionDuration = resources.getInteger(R.integer.default_fragment_transition).toLong()
        val backgroundColor = ContextCompat.getColor(requireContext(), R.color.default_window_background)
        sharedElementEnterTransition = MaterialContainerTransform().apply {
            drawingViewId = R.id.snack_root
            duration = transitionDuration
            scrimColor = Color.TRANSPARENT
            startContainerColor = backgroundColor
            endContainerColor = backgroundColor
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentProductDetailBinding.bind(view)
        setHasOptionsMenu(true)

        ViewCompat.setTransitionName(
            binding.root,
            getString(R.string.product_card_detail_transition_name)
        )
        initializeViews(savedInstanceState)
        initializeViewModel()
    }

    override fun onDestroyView() {
        skeletonView.hide()
        imageUploadErrorsSnackbar?.dismiss()
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onPause() {
        super.onPause()
        progressDialog?.dismiss()
    }

    override fun onStop() {
        super.onStop()
        WooDialog.onCleared()
    }

    private fun initializeViews(savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
        this.layoutManager = layoutManager

        savedInstanceState?.getParcelable<Parcelable>(LIST_STATE_KEY)?.let {
            layoutManager.onRestoreInstanceState(it)
        }
        binding.cardsRecyclerView.layoutManager = layoutManager
        binding.cardsRecyclerView.itemAnimator = null
    }

    private fun initializeViewModel() {
        setupObservers(viewModel)
        setupResultHandlers(viewModel)
    }

    private fun setupResultHandlers(viewModel: ProductDetailViewModel) {
        handleResult<ProductTypesBottomSheetUiItem>(ProductTypesBottomSheetFragment.KEY_PRODUCT_TYPE_RESULT) {
            viewModel.updateProductDraft(type = it.type.value, isVirtual = it.isVirtual)
            changesMade()
        }
        handleResult<List<Long>>(GroupedProductListType.GROUPED.resultKey) {
            viewModel.updateProductDraft(groupedProductIds = it)
            changesMade()
        }
        handleResult<PricingData>(BaseProductEditorFragment.KEY_PRICING_DIALOG_RESULT) {
            viewModel.updateProductDraft(
                regularPrice = it.regularPrice,
                salePrice = it.salePrice,
                saleStartDate = it.saleStartDate,
                saleEndDate = it.saleEndDate,
                isSaleScheduled = it.isSaleScheduled,
                taxClass = it.taxClass,
                taxStatus = it.taxStatus
            )
            changesMade()
        }
        handleResult<InventoryData>(BaseProductEditorFragment.KEY_INVENTORY_DIALOG_RESULT) {
            viewModel.updateProductDraft(
                sku = it.sku,
                soldIndividually = it.isSoldIndividually,
                stockStatus = it.stockStatus,
                stockQuantity = it.stockQuantity,
                backorderStatus = it.backorderStatus,
                manageStock = it.isStockManaged
            )
            changesMade()
        }
        handleResult<ShippingData>(BaseProductEditorFragment.KEY_SHIPPING_DIALOG_RESULT) {
            viewModel.updateProductDraft(
                weight = it.weight,
                length = it.length,
                width = it.width,
                height = it.height,
                shippingClass = it.shippingClassSlug,
                shippingClassId = it.shippingClassId
            )
            changesMade()
        }
        handleResult<List<Image>>(BaseProductEditorFragment.KEY_IMAGES_DIALOG_RESULT) {
            viewModel.updateProductDraft(images = it)
            changesMade()
        }

        handleResult<Bundle>(AztecEditorFragment.AZTEC_EDITOR_RESULT) { result ->
            if (!result.getBoolean(AztecEditorFragment.ARG_AZTEC_HAS_CHANGES)) return@handleResult
            when (result.getInt(AztecEditorFragment.ARG_AZTEC_REQUEST_CODE)) {
                RequestCodes.AZTEC_EDITOR_PRODUCT_DESCRIPTION -> {
                    viewModel.updateProductDraft(description = result.getString(ARG_AZTEC_EDITOR_TEXT))
                }
                RequestCodes.AZTEC_EDITOR_PRODUCT_SHORT_DESCRIPTION -> {
                    viewModel.updateProductDraft(shortDescription = result.getString(ARG_AZTEC_EDITOR_TEXT))
                }
            }
            changesMade()
        }

        handleResult<VariationListData>(VariationListFragment.KEY_VARIATION_LIST_RESULT) { data ->
            data.currentVariationAmount?.let { viewModel.onVariationAmountReceived(it) }
        }
    }

    private fun setupObservers(viewModel: ProductDetailViewModel) {
        viewModel.productDetailViewStateData.observe(viewLifecycleOwner) { old, new ->
            new.productDraft?.takeIfNotEqualTo(old?.productDraft) { showProductDetails(it) }
            new.isProductUpdated?.takeIfNotEqualTo(old?.isProductUpdated) { requireActivity().invalidateOptionsMenu() }
            new.isSkeletonShown?.takeIfNotEqualTo(old?.isSkeletonShown) { showSkeleton(it) }
            new.isProgressDialogShown?.takeIfNotEqualTo(old?.isProgressDialogShown) {
                if (it) {
                    showProgressDialog(R.string.product_save_dialog_title, R.string.product_update_dialog_message)
                } else {
                    hideProgressDialog()
                }
            }
            new.uploadingImageUris?.takeIfNotEqualTo(old?.uploadingImageUris) {
                binding.imageGallery.setPlaceholderImageUris(it)
            }
            new.showBottomSheetButton?.takeIfNotEqualTo(old?.showBottomSheetButton) { isVisible ->
                binding.productDetailAddMoreContainer.isVisible = isVisible
            }
            new.isUploadingDownloadableFile?.takeIfNotEqualTo(old?.isUploadingDownloadableFile) {
                if (it) {
                    showProgressDialog(
                        title = R.string.product_downloadable_files_upload_dialog_title,
                        message = R.string.product_downloadable_files_upload_dialog_message
                    )
                } else {
                    hideProgressDialog()
                }
            }
        }

        viewModel.productDetailCards.observe(
            viewLifecycleOwner,
            Observer {
                showProductCards(it)
            }
        )

        viewModel.event.observe(
            viewLifecycleOwner,
            Observer { event ->
                when (event) {
                    is LaunchUrlInChromeTab -> {
                        ChromeCustomTabUtils.launchUrl(requireContext(), event.url)
                    }
                    is RefreshMenu -> activity?.invalidateOptionsMenu()
                    is ExitWithResult<*> -> {
                        navigateBackWithResult(
                            KEY_PRODUCT_DETAIL_RESULT,
                            Bundle().also {
                                it.putLong(KEY_REMOTE_PRODUCT_ID, event.data as Long)
                                it.putBoolean(KEY_PRODUCT_DETAIL_DID_TRASH, true)
                            }
                        )
                    }
                    is ShowActionSnackbar -> displayProductImageUploadErrorSnackBar(event.message, event.action)
                    is HideImageUploadErrorSnackbar -> imageUploadErrorsSnackbar?.dismiss()
                    else -> event.isHandled = false
                }
            }
        )
    }

    private fun showProductDetails(product: Product) {
        productName = updateProductNameFromDetails(product)
        productId = product.remoteId

        if (product.images.isEmpty() && !viewModel.isUploadingImages()) {
            binding.imageGallery.hide()
            startAddImageContainer()
        } else {
            binding.addImageContainer.hide()
            binding.imageGallery.show()
            binding.imageGallery.showProductImages(product.images, this)
        }

        // show status badge for unpublished products
        product.status?.let { status ->
            if (status != ProductStatus.PUBLISH && viewModel.isAddFlowEntryPoint.not()) {
                binding.frameStatusBadge.show()
                binding.textStatusBadge.text = status.toLocalizedString(requireActivity())
            } else {
                binding.frameStatusBadge.hide()
            }
        }

        binding.productDetailAddMoreButton.setOnClickListener {
            // TODO: add tracking events here
            viewModel.onEditProductCardClicked(
                ViewProductDetailBottomSheet(product.productType)
            )
        }

        requireActivity().invalidateOptionsMenu()
    }

    private fun updateProductNameFromDetails(product: Product): String {
        return if (viewModel.isProductUnderCreation && product.name.isEmpty()) {
            getString(R.string.product_add_tool_bar_title)
        } else product.name.fastStripHtml()
    }

    private fun displayProductImageUploadErrorSnackBar(
        message: String,
        actionListener: View.OnClickListener
    ) {
        if (imageUploadErrorsSnackbar == null) {
            imageUploadErrorsSnackbar = uiMessageResolver.getIndefiniteActionSnack(
                message = message,
                actionText = getString(R.string.details),
                actionListener = actionListener
            )
        } else {
            imageUploadErrorsSnackbar?.setText(message)
        }
        imageUploadErrorsSnackbar?.show()
    }

    private fun startAddImageContainer() {
        binding.addImageContainer.show()
        binding.addImageContainer.setOnClickListener {
            AnalyticsTracker.track(AnalyticsEvent.PRODUCT_DETAIL_ADD_IMAGE_TAPPED)
            viewModel.onAddImageButtonClicked()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_product_detail_fragment, menu)
        saveMenuItem = menu.findItem(R.id.menu_save)
        publishMenuItem = menu.findItem(R.id.menu_publish)

        super.onCreateOptionsMenu(menu, inflater)
    }

    @SuppressLint("ResourceAsColor")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        fun Menu.printItems(): String = buildString {
            this@printItems.forEach {
                append("${resources.getResourceName(it.itemId)}\n")
            }
        }

        // Some users are experiencing a crash because the entry R.id.menu_view_product is missing from the menu
        // see: https://github.com/woocommerce/woocommerce-android/issues/3241
        // If this happens, we will send the below report, and we avoid the crash using the null checks below
        // TODO: remove the null checks once the root cause is identified is fixed
        if (menu.findItem(R.id.menu_view_product) == null) {
            val message = """menu.findItem(R.id.menu_view_product) is null
                |User is ${if (viewModel.isProductUnderCreation) "creating a product" else "modifying a product"}
                |menu elements:
                |${menu.printItems()}
            """.trimMargin()
            crashLogging.sendReport(exception = NullPointerException(message))
        }

        // visibility of these menu items depends on whether we're in the add product flow
        menu.findItem(R.id.menu_view_product)?.isVisible =
            viewModel.isProductPublished &&
            !viewModel.isProductUnderCreation

        menu.findItem(R.id.menu_share)?.isVisible = !viewModel.isProductUnderCreation
        menu.findItem(R.id.menu_product_settings)?.isVisible = true

        // change the font color of the trash menu item to red, and only show it if it should be enabled
        with(menu.findItem(R.id.menu_trash_product)) {
            if (this == null) return@with
            val title = SpannableString(this.title)
            title.setSpan(
                ForegroundColorSpan(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.woo_red_30
                    )
                ),
                0,
                title.length,
                0
            )
            this.title = title
            this.isVisible = viewModel.isTrashEnabled
        }

        menu.findItem(R.id.menu_save_as_draft)?.isVisible = viewModel.canBeStoredAsDraft && viewModel.hasChanges()

        saveMenuItem?.isVisible = viewModel.isSaveOptionNeeded
        publishMenuItem?.isVisible = viewModel.isPublishOptionNeeded

        if (saveMenuItem?.isVisible ?: false) {
            publishMenuItem?.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER)
            publishMenuItem?.title = getString(R.string.product_add_tool_bar_menu_button_done)
        } else {
            publishMenuItem?.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            publishMenuItem?.title =
                getString(R.string.product_add_tool_bar_menu_button_done).uppercase(Locale.getDefault())
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_publish -> {
                ActivityUtils.hideKeyboard(activity)
                viewModel.onUpdateButtonClicked(isPublish = true)
                true
            }

            R.id.menu_save_as_draft -> {
                viewModel.onSaveAsDraftButtonClicked()
                true
            }

            R.id.menu_share -> {
                viewModel.onShareButtonClicked()
                true
            }

            R.id.menu_save -> {
                ActivityUtils.hideKeyboard(activity)
                viewModel.onUpdateButtonClicked(isPublish = false)
                true
            }

            R.id.menu_view_product -> {
                viewModel.onViewProductOnStoreLinkClicked()
                true
            }

            R.id.menu_product_settings -> {
                viewModel.onSettingsButtonClicked()
                true
            }

            R.id.menu_trash_product -> {
                viewModel.onTrashButtonClicked()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun changesMade() {
        requireActivity().invalidateOptionsMenu()
    }

    private fun showSkeleton(show: Boolean) {
        if (show) {
            skeletonView.show(binding.appBarLayout, R.layout.skeleton_product_detail, delayed = true)
        } else {
            skeletonView.hide()
        }
    }

    private fun showProgressDialog(@StringRes title: Int, @StringRes message: Int) {
        hideProgressDialog()
        progressDialog = CustomProgressDialog.show(
            getString(title),
            getString(message)
        ).also { it.show(parentFragmentManager, CustomProgressDialog.TAG) }
        progressDialog?.isCancelable = false
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun showProductCards(cards: List<ProductPropertyCard>) {
        val adapter: ProductPropertyCardsAdapter
        if (binding.cardsRecyclerView.adapter == null) {
            adapter = ProductPropertyCardsAdapter()
            binding.cardsRecyclerView.adapter = adapter
        } else {
            adapter = binding.cardsRecyclerView.adapter as ProductPropertyCardsAdapter
        }

        val recyclerViewState = binding.cardsRecyclerView.layoutManager?.onSaveInstanceState()
        adapter.update(cards)
        binding.cardsRecyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        layoutManager?.let {
            outState.putParcelable(LIST_STATE_KEY, it.onSaveInstanceState())
        }
    }

    override fun onRequestAllowBackPress(): Boolean {
        return viewModel.onBackButtonClickedProductDetail()
    }

    override fun onGalleryImageClicked(image: Image) {
        viewModel.onImageClicked(image)
    }

    override fun onGalleryAddImageClicked() {
        AnalyticsTracker.track(AnalyticsEvent.PRODUCT_DETAIL_ADD_IMAGE_TAPPED)
        viewModel.onAddImageButtonClicked()
    }

    override fun getFragmentTitle(): String = productName
}
