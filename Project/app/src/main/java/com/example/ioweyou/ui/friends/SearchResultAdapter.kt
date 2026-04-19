package com.example.ioweyou.ui.friends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ioweyou.databinding.RowSearchResultBinding
import com.example.ioweyou.model.User

class SearchResultAdapter(
    private val onAddFriend: (User) -> Unit
) : ListAdapter<User, SearchResultAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: RowSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.tvName.text = user.displayName.ifEmpty { user.email }
            binding.tvEmail.text = user.email
            binding.btnAddFriend.setOnClickListener { onAddFriend(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowSearchResultBinding.inflate(
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
