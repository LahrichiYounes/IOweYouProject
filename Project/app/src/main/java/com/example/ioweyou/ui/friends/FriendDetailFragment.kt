package com.example.ioweyou.ui.friends

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.ioweyou.databinding.FragmentFriendDetailBinding
import com.example.ioweyou.ui.MainViewModel
import com.example.ioweyou.ui.home.ExpenseRowAdapter
import java.text.NumberFormat
import java.util.Locale

class FriendDetailFragment : Fragment() {

    private var _binding: FragmentFriendDetailBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val args: FriendDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currency = NumberFormat.getCurrencyInstance()
        val friend = viewModel.friends.value?.find { it.uid == args.friendUid }

        if (friend != null) {
            binding.tvFriendDetailName.text = friend.displayName.ifEmpty { friend.email }
            if (friend.photoUrl.isNotEmpty()) {
                Glide.with(this).load(friend.photoUrl).circleCrop().into(binding.ivFriendDetailAvatar)
            }
        }

        val adapter = ExpenseRowAdapter(viewModel.currentUser.uid) {}
        binding.rvSharedExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSharedExpenses.adapter = adapter

        viewModel.expenses.observe(viewLifecycleOwner) { expenses ->
            val shared = expenses.filter { it.participants.contains(args.friendUid) }
            adapter.submitList(shared)

            val uid = viewModel.currentUser.uid
            var net = 0.0
            for (expense in shared) {
                if (expense.settled) continue
                if (expense.paidBy == uid) {
                    net += expense.owedBy(args.friendUid)
                } else if (expense.paidBy == args.friendUid) {
                    net -= expense.owedBy(uid)
                }
            }

            val friendName = friend?.displayName?.ifEmpty { "They" } ?: "They"
            binding.tvFriendBalance.text = when {
                net > 0.01  -> "$friendName owes you ${currency.format(net)}"
                net < -0.01 -> "You owe $friendName ${currency.format(-net)}"
                else        -> "You're all settled up"
            }

            if (net < -0.01) {
                binding.btnVenmoFriend.visibility = View.VISIBLE
                binding.btnVenmoFriend.text = "Pay ${currency.format(-net)} via Venmo"
                binding.btnVenmoFriend.setOnClickListener {
                    val amount = String.format(Locale.US, "%.2f", -net)
                    val uri = Uri.parse("venmo://paycharge?txn=pay&amount=$amount&note=${Uri.encode("IOweYou settlement")}")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    if (intent.resolveActivity(requireContext().packageManager) != null) {
                        startActivity(intent)
                    } else {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://venmo.com/")))
                    }
                }
            } else {
                binding.btnVenmoFriend.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
