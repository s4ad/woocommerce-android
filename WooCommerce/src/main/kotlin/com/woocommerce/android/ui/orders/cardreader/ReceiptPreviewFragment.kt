package com.woocommerce.android.ui.orders.cardreader

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.databinding.FragmentReceiptPreviewBinding
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.base.UIMessageResolver
import com.woocommerce.android.ui.common.wpcomwebview.UrlInterceptor
import com.woocommerce.android.ui.common.wpcomwebview.WPComWebViewClient
import com.woocommerce.android.ui.orders.cardreader.ReceiptEvent.PrintReceipt
import com.woocommerce.android.ui.orders.cardreader.ReceiptEvent.SendReceipt
import com.woocommerce.android.ui.orders.cardreader.ReceiptPreviewViewModel.ReceiptPreviewEvent.LoadUrl
import com.woocommerce.android.util.ActivityUtils
import com.woocommerce.android.util.PrintHtmlHelper
import com.woocommerce.android.util.UiHelpers
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import dagger.hilt.android.AndroidEntryPoint
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.util.StringUtils
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

private const val WPCOM_LOGIN_URL = "https://wordpress.com/wp-login.php"

@AndroidEntryPoint
class ReceiptPreviewFragment : BaseFragment(R.layout.fragment_receipt_preview), UrlInterceptor {
    val viewModel: ReceiptPreviewViewModel by viewModels()

    private val webViewClient by lazy { WPComWebViewClient(this) }

    @Inject lateinit var printHtmlHelper: PrintHtmlHelper
    @Inject lateinit var uiMessageResolver: UIMessageResolver
    @Inject lateinit var accountStore: AccountStore
    @Inject lateinit var userAgent: UserAgent

    private var _binding: FragmentReceiptPreviewBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentReceiptPreviewBinding.bind(view)
        initViews(binding, savedInstanceState)
        initObservers(binding)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_receipt_preview, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_print -> {
                viewModel.onPrintClicked()
                true
            }
            R.id.menu_send -> {
                viewModel.onSendEmailClicked()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
        printHtmlHelper.getAndClearPrintJobResult()?.let {
            viewModel.onPrintResult(it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.receiptPreviewPreviewWebview.saveState(outState)
    }

    override fun onLoadUrl(url: String) {
        // todo pass to view model and verify if it's the receipt url
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initViews(binding: FragmentReceiptPreviewBinding, savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        if (savedInstanceState != null) {
            binding.receiptPreviewPreviewWebview.restoreState(savedInstanceState)
        } else {
            with(binding.receiptPreviewPreviewWebview) {
                this.webViewClient = this@ReceiptPreviewFragment.webViewClient
                this.webChromeClient = object : WebChromeClient() {
                    @Suppress("MagicNumber")
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    }
                }
                this.settings.javaScriptEnabled = true
                settings.userAgentString = userAgent.getUserAgent()
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
            }
        }
    }

    private fun initObservers(binding: FragmentReceiptPreviewBinding) {
        viewModel.viewStateData.observe(viewLifecycleOwner) {
            UiHelpers.updateVisibility(binding.receiptPreviewPreviewWebview, it.isContentVisible)
            UiHelpers.updateVisibility(binding.receiptPreviewProgressBar, it.isProgressVisible)
        }
        viewModel.event.observe(viewLifecycleOwner) {
            when (it) {
                is LoadUrl -> loadAuthenticatedUrl(binding.receiptPreviewPreviewWebview, it.url)
                is PrintReceipt -> printHtmlHelper.printReceipt(requireActivity(), it.receiptUrl, it.documentName)
                is SendReceipt -> composeEmail(it)
                is ShowSnackbar -> uiMessageResolver.showSnack(it.message)
                else -> it.isHandled = false
            }
        }
    }

    private fun composeEmail(event: SendReceipt) {
        val success = ActivityUtils.composeEmail(requireActivity(), event.address, event.subject, event.content)
        if (!success) viewModel.onEmailActivityNotFound()
    }

    private fun loadAuthenticatedUrl(webView: WebView, urlToLoad: String) {
        val postData = getAuthenticationPostData(
            urlToLoad = urlToLoad,
            username = accountStore.account.userName,
            token = accountStore.accessToken
        )

        webView.postUrl(WPCOM_LOGIN_URL, postData.toByteArray())
    }

    fun getAuthenticationPostData(urlToLoad: String, username: String, token: String): String {
        val utf8 = StandardCharsets.UTF_8.name()
        try {
            var postData = String.format(
                "log=%s&redirect_to=%s",
                URLEncoder.encode(StringUtils.notNullStr(username), utf8),
                URLEncoder.encode(StringUtils.notNullStr(urlToLoad), utf8)
            )

            // Add token authorization
            postData += "&authorization=Bearer " + URLEncoder.encode(token, utf8)

            return postData
        } catch (e: UnsupportedEncodingException) {
            WooLog.e(WooLog.T.UTILS, e)
        }
        return ""
    }

}
