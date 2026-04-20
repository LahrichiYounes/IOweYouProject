package com.example.ioweyou.ui.scan

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.ioweyou.databinding.RowLineItemBinding
import com.example.ioweyou.model.LineItem
import com.example.ioweyou.model.User

class LineItemAdapter(
    private val items: MutableList<LineItem>,
    private val getFriends: () -> List<User>
) : RecyclerView.Adapter<LineItemAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: RowLineItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var nameWatcher: TextWatcher? = null
        private var priceWatcher: TextWatcher? = null

        fun bind(position: Int) {
            val item = items[position]

            nameWatcher?.let { binding.etItemName.removeTextChangedListener(it) }
            priceWatcher?.let { binding.etItemPrice.removeTextChangedListener(it) }

            binding.etItemName.setText(item.name)
            binding.etItemPrice.setText(if (item.price > 0) item.price.toString() else "")
            binding.tvAssignees.text = if (item.assignees.isEmpty()) "Tap to assign"
            else getFriends().filter { it.uid in item.assignees }
                .joinToString(", ") { it.displayName.ifEmpty { it.email } }

            nameWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    items[adapterPosition] = items[adapterPosition].copy(name = s.toString())
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            priceWatcher = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val price = s.toString().toDoubleOrNull() ?: 0.0
                    items[adapterPosition] = items[adapterPosition].copy(price = price)
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            binding.etItemName.addTextChangedListener(nameWatcher)
            binding.etItemPrice.addTextChangedListener(priceWatcher)

            binding.tvAssignees.setOnClickListener { showAssignDialog(position) }

            binding.btnDeleteItem.setOnClickListener {
                items.removeAt(adapterPosition)
                notifyItemRemoved(adapterPosition)
            }
        }

        private fun showAssignDialog(position: Int) {
            val friends = getFriends()
            if (friends.isEmpty()) {
                AlertDialog.Builder(binding.root.context)
                    .setMessage("Add friends first before assigning items.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }
            val names = friends.map { it.displayName.ifEmpty { it.email } }.toTypedArray()
            val currentAssignees = items[position].assignees
            val checked = BooleanArray(friends.size) { friends[it].uid in currentAssignees }

            AlertDialog.Builder(binding.root.context)
                .setTitle("Assign to friends")
                .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton("Done") { _, _ ->
                    val selected = friends.filterIndexed { i, _ -> checked[i] }.map { it.uid }
                    items[position] = items[position].copy(assignees = selected)
                    notifyItemChanged(position)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowLineItemBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount() = items.size

    fun getItems(): List<LineItem> = items.toList()
}
