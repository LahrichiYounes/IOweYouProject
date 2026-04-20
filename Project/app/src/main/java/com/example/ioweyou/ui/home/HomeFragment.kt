package com.example.ioweyou.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ioweyou.R
import com.example.ioweyou.databinding.FragmentHomeBinding
import com.example.ioweyou.ui.MainViewModel
import java.text.NumberFormat

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: ExpenseRowAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currency = NumberFormat.getCurrencyInstance()

        adapter = ExpenseRowAdapter(viewModel.currentUser.uid) { expense ->
            val action = HomeFragmentDirections
                .actionHomeFragmentToExpenseDetailFragment(expense.id)
            findNavController().navigate(action)
        }

        binding.rvExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExpenses.adapter = adapter

        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            adapter.submitList(expenses)

            val uid = viewModel.currentUser.uid
            var totalOwed = 0.0
            var totalOwing = 0.0

            for (expense in expenses) {
                if (expense.settled) continue
                if (expense.paidBy == uid) {
                    totalOwed += expense.owedTo(uid)
                } else {
                    totalOwing += expense.owedBy(uid)
                }
            }

            val net = totalOwed - totalOwing
            binding.tvBalanceSummary.text = when {
                net > 0.01  -> "You are owed ${currency.format(net)} overall"
                net < -0.01 -> "You owe ${currency.format(-net)} overall"
                else        -> "All settled up!"
            }
        }

        binding.fabNewExpense.setOnClickListener {
            findNavController().navigate(R.id.scanFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
