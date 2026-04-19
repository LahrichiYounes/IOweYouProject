package com.example.ioweyou.ui.friends

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ioweyou.databinding.FragmentFriendsBinding
import com.example.ioweyou.model.User
import com.example.ioweyou.ui.MainViewModel

class FriendsFragment : Fragment() {

    private var _binding: FragmentFriendsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var friendAdapter: FriendRowAdapter
    private lateinit var requestAdapter: FriendRequestAdapter
    private lateinit var searchAdapter: SearchResultAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFriendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        friendAdapter = FriendRowAdapter(viewModel.currentUser.uid) { friend ->
            // Could navigate to a per-friend detail screen in future
        }
        requestAdapter = FriendRequestAdapter(
            onAccept = { request -> viewModel.acceptFriendRequest(request) },
            onDecline = { request -> viewModel.declineFriendRequest(request.id) }
        )
        searchAdapter = SearchResultAdapter { user ->
            viewModel.sendFriendRequest(user)
            viewModel.clearSearchResults()
            binding.etSearch.setText("")
            Toast.makeText(requireContext(), "Friend request sent to ${user.displayName}", Toast.LENGTH_SHORT).show()
        }

        binding.rvFriendRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriendRequests.adapter = requestAdapter

        binding.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriends.adapter = friendAdapter

        binding.rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSearchResults.adapter = searchAdapter

        viewModel.friendRequests.observe(viewLifecycleOwner) { requests ->
            requestAdapter.submitList(requests)
            binding.tvPendingHeader.visibility = if (requests.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.friends.observe(viewLifecycleOwner) { friends ->
            val balances = computeBalances()
            friendAdapter.submitList(friends, balances)
        }

        viewModel.expenses.observe(viewLifecycleOwner) { _ ->
            val balances = computeBalances()
            friendAdapter.updateBalances(balances)
        }

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            val alreadyFriends = viewModel.friends.value?.map { it.uid } ?: emptyList()
            val filtered = results.filter { it.uid != viewModel.currentUser.uid && it.uid !in alreadyFriends }
            searchAdapter.submitList(filtered)
            binding.rvSearchResults.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.length >= 3) {
                    viewModel.searchUsers(query)
                } else {
                    viewModel.clearSearchResults()
                    binding.rvSearchResults.visibility = View.GONE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun computeBalances(): Map<String, Double> {
        val uid = viewModel.currentUser.uid
        val map = mutableMapOf<String, Double>()
        for (expense in viewModel.expenses.value ?: emptyList()) {
            if (expense.settled) continue
            if (expense.paidBy == uid) {
                for (p in expense.participants) {
                    if (p == uid) continue
                    map[p] = (map[p] ?: 0.0) + expense.owedBy(p)
                }
            } else {
                map[expense.paidBy] = (map[expense.paidBy] ?: 0.0) - expense.owedBy(uid)
            }
        }
        return map
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
