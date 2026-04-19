package com.example.ioweyou.ui.friends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ioweyou.databinding.RowFriendBinding
import com.example.ioweyou.model.User
import java.text.NumberFormat

class FriendRowAdapter(
    private val currentUid: String,
    private val onClick: (User) -> Unit
) : ListAdapter<User, FriendRowAdapter.ViewHolder>(DIFF) {

    private var balances: Map<String, Double> = emptyMap()

    fun submitList(friends: List<User>, balances: Map<String, Double>) {
        this.balances = balances
        submitList(friends)
    }

    fun updateBalances(newBalances: Map<String, Double>) {
        this.balances = newBalances
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: RowFriendBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            val currency = NumberFormat.getCurrencyInstance()
            binding.tvFriendName.text = user.displayName.ifEmpty { user.email }
            binding.tvFriendEmail.text = user.email

            val balance = balances[user.uid] ?: 0.0
            binding.tvBalance.text = when {
                balance > 0.01  -> "Owes you ${currency.format(balance)}"
                balance < -0.01 -> "You owe ${currency.format(-balance)}"
                else            -> "Settled up"
            }

            if (user.photoUrl.isNotEmpty()) {
                Glide.with(binding.root).load(user.photoUrl).circleCrop()
                    .into(binding.ivAvatar)
            }
            binding.root.setOnClickListener { onClick(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowFriendBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(a: User, b: User) = a.uid == b.uid
            override fun areContentsTheSame(a: User, b: User) = a == b
        }
    }
}
