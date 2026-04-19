package com.example.ioweyou.ui.friends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ioweyou.databinding.RowFriendRequestBinding
import com.example.ioweyou.model.FriendRequest

class FriendRequestAdapter(
    private val onAccept: (FriendRequest) -> Unit,
    private val onDecline: (FriendRequest) -> Unit
) : ListAdapter<FriendRequest, FriendRequestAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: RowFriendRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(request: FriendRequest) {
            binding.tvRequesterName.text = request.fromName.ifEmpty { request.fromEmail }
            binding.tvRequesterEmail.text = request.fromEmail
            binding.btnAccept.setOnClickListener { onAccept(request) }
            binding.btnDecline.setOnClickListener { onDecline(request) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowFriendRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<FriendRequest>() {
            override fun areItemsTheSame(a: FriendRequest, b: FriendRequest) = a.id == b.id
            override fun areContentsTheSame(a: FriendRequest, b: FriendRequest) = a == b
        }
    }
}
