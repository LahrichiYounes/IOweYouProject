package com.example.ioweyou.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ioweyou.databinding.FragmentExpenseDetailBinding
import com.example.ioweyou.model.Expense
import com.example.ioweyou.ui.MainViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpenseDetailFragment : Fragment() {

    private var _binding: FragmentExpenseDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val args: ExpenseDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpenseDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            val expense = expenses.find { it.id == args.expenseId } ?: return@observe
            bindExpense(expense)
        }
    }

    private fun bindExpense(expense: Expense) {
        val currency = NumberFormat.getCurrencyInstance()
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())

        binding.tvDetailTitle.text = expense.title
        binding.tvDetailDate.text = dateFormat.format(Date(expense.timestamp))
        binding.tvDetailTotal.text = "Total: ${currency.format(expense.totalAmount)}"
        binding.tvPaidBy.text = "Paid by: ${expense.paidByName}"

        val uid = viewModel.currentUser.uid
        val friends = viewModel.friends.value ?: emptyList()

        val sb = StringBuilder()
        for (participantUid in expense.participants) {
            if (participantUid == expense.paidBy) continue
            val name = if (participantUid == uid) "You"
            else friends.find { it.uid == participantUid }?.displayName ?: participantUid
            val owed = expense.owedBy(participantUid)
            if (owed > 0.01) {
                sb.appendLine("$name owes ${currency.format(owed)}")
            }
        }
        binding.tvBreakdown.text = sb.toString().trimEnd()

        if (expense.settled) {
            binding.btnSettle.visibility = View.GONE
            binding.tvSettledBadge.visibility = View.VISIBLE
        } else {
            binding.tvSettledBadge.visibility = View.GONE
            binding.btnSettle.visibility = View.VISIBLE
            binding.btnSettle.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Mark as settled?")
                    .setMessage("This will mark the expense as fully settled for all participants.")
                    .setPositiveButton("Settle") { _, _ ->
                        viewModel.markSettled(expense.id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        if (expense.paidBy != uid) {
            val myShare = expense.owedBy(uid)
            val payerName = expense.paidByName
            binding.btnVenmo.visibility = if (myShare > 0.01) View.VISIBLE else View.GONE
            binding.btnVenmo.text = "Pay ${currency.format(myShare)} via Venmo"
            binding.btnVenmo.setOnClickListener {
                launchVenmo(myShare, "IOweYou: ${expense.title}")
            }
        } else {
            binding.btnVenmo.visibility = View.GONE
        }
    }

    private fun launchVenmo(amount: Double, note: String) {
        val formattedAmount = String.format(Locale.US, "%.2f", amount)
        val venmoUri = Uri.parse(
            "venmo://paycharge?txn=pay&amount=$formattedAmount&note=${Uri.encode(note)}"
        )
        val intent = Intent(Intent.ACTION_VIEW, venmoUri)
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://venmo.com/")))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
