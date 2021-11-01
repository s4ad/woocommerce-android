package com.woocommerce.android.ui.orders.filters

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.woocommerce.android.R
import com.woocommerce.android.databinding.FragmentOrderFilterListBinding
import com.woocommerce.android.extensions.navigateBackWithResult
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.orders.filters.adapter.OrderFilterOptionAdapter
import com.woocommerce.android.ui.orders.filters.model.OrderFilterOptionUiModel
import com.woocommerce.android.ui.orders.list.OrderListFragment
import com.woocommerce.android.viewmodel.MultiLiveEvent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OrderFilterOptionListFragment :
    BaseFragment(R.layout.fragment_order_filter_list) {
    private val viewModel: OrderFilterOptionListViewModel by viewModels()

    lateinit var orderFilterOptionAdapter: OrderFilterOptionAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        val binding = FragmentOrderFilterListBinding.bind(view)
        setUpObservers(viewModel)
        setUpFilterOptionsRecyclerView(binding)
        binding.filterListBtnShowOrders.setOnClickListener {
            viewModel.onShowOrdersClicked()
        }
    }

    private fun setUpFilterOptionsRecyclerView(binding: FragmentOrderFilterListBinding) {
        orderFilterOptionAdapter = OrderFilterOptionAdapter(
            onFilterOptionClicked = { selectedFilterOption ->
                viewModel.onFilterOptionSelected(selectedFilterOption)
            }
        )
        binding.filterList.apply {
            layoutManager = LinearLayoutManager(requireActivity())
            adapter = orderFilterOptionAdapter
            addItemDecoration(
                DividerItemDecoration(
                    requireActivity(),
                    DividerItemDecoration.VERTICAL
                )
            )
        }
    }

    private fun setUpObservers(viewModel: OrderFilterOptionListViewModel) {
        viewModel.orderFilterOptions.observe(viewLifecycleOwner) { filterOptions ->
            showOrderFilterOptions(filterOptions)
        }
        viewModel.orderFilterOptionScreenTitle.observe(viewLifecycleOwner) { title ->
            requireActivity().title = title
        }
        viewModel.event.observe(viewLifecycleOwner) { event ->
            when (event) {
                is MultiLiveEvent.Event.ExitWithResult<*> -> navigateBackWithResult(
                    OrderListFragment.ORDER_FILTER_RESULT_KEY,
                    event.data,
                    R.id.orders
                )
                else -> event.isHandled = false
            }
        }
    }

    private fun showOrderFilterOptions(orderFilterOptions: List<OrderFilterOptionUiModel>) {
        orderFilterOptionAdapter.submitList(orderFilterOptions)
    }
}
