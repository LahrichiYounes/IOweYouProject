package com.example.ioweyou.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ioweyou.databinding.RowExpenseBinding
import com.example.ioweyou.model.Expense
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpenseRowAdapter(
    private val currentUid: String,
    private val onClick: (Expense) -> Unit
) : ListAdapter<Expense, ExpenseRowAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: RowExpenseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(expense: Expense) {
            val currency = NumberFormat.getCurrencyInstance()
            val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

            binding.tvExpenseTitle.text = expense.title
            binding.tvExpenseDate.text = dateFormat.format(Date(expense.timestamp))
            binding.tvExpenseTotal.text = currency.format(expense.totalAmount)

            if (expense.settled) {
                binding.tvExpenseYourShare.text = "Settled"
            } else if (expense.paidBy == currentUid) {
                val owed = expense.owedTo(currentUid)
                binding.tvExpenseYourShare.text = if (owed > 0.01)
                    "You get back ${currency.format(owed)}" else "You paid"
            } else {
                val share = expense.owedBy(currentUid)
                binding.tvExpenseYourShare.text = if (share > 0.01)
                    "You owe ${currency.format(share)}" else "Your share: $0.00"
            }

            binding.root.setOnClickListener { onClick(expense) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowExpenseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Expense>() {
            override fun areItemsTheSame(a: Expense, b: Expense) = a.id == b.id
            override fun areContentsTheSame(a: Expense, b: Expense) = a == b
        }
    }
}
