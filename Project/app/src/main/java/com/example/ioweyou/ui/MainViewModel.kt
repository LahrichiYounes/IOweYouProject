package com.example.ioweyou.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ioweyou.model.Expense
import com.example.ioweyou.model.FriendRequest
import com.example.ioweyou.model.LineItem
import com.example.ioweyou.model.User
import com.example.ioweyou.repository.FirestoreRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val repo = FirestoreRepository()
    private val auth = FirebaseAuth.getInstance()

    val currentUser: User get() {
        val fb = auth.currentUser!!
        return User(
            uid = fb.uid,
            displayName = fb.displayName ?: "",
            email = fb.email ?: "",
            photoUrl = fb.photoUrl?.toString() ?: ""
        )
    }

    private val _expenses = MutableLiveData<List<Expense>>(emptyList())
    val expenses: LiveData<List<Expense>> = _expenses
    private var expenseListener: ListenerRegistration? = null

    private val _friends = MutableLiveData<List<User>>(emptyList())
    val friends: LiveData<List<User>> = _friends
    private var friendListener: ListenerRegistration? = null

    private val _friendRequests = MutableLiveData<List<FriendRequest>>(emptyList())
    val friendRequests: LiveData<List<FriendRequest>> = _friendRequests
    private var requestListener: ListenerRegistration? = null

    private val _searchResults = MutableLiveData<List<User>>(emptyList())
    val searchResults: LiveData<List<User>> = _searchResults

    val balances: LiveData<Map<String, Double>> get() {
        val result = MutableLiveData<Map<String, Double>>()
        result.value = computeBalances()
        return result
    }

    fun startListening() {
        val uid = currentUser.uid

        expenseListener = repo.listenToExpenses(uid) { list ->
            _expenses.value = list
        }
        friendListener = repo.listenToFriends(uid) { list ->
            _friends.value = list
        }
        requestListener = repo.listenToIncomingRequests(uid) { list ->
            _friendRequests.value = list
        }
    }

    private fun computeBalances(): Map<String, Double> {
        val uid = currentUser.uid
        val map = mutableMapOf<String, Double>()
        for (expense in _expenses.value ?: emptyList()) {
            if (expense.settled) continue
            if (expense.paidBy == uid) {
                for (participantUid in expense.participants) {
                    if (participantUid == uid) continue
                    val owed = expense.owedBy(participantUid)
                    map[participantUid] = (map[participantUid] ?: 0.0) + owed
                }
            } else {
                val owed = expense.owedBy(uid)
                map[expense.paidBy] = (map[expense.paidBy] ?: 0.0) - owed
            }
        }
        return map
    }

    fun searchUsers(query: String) {
        viewModelScope.launch {
            _searchResults.value = repo.searchUsers(query)
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    fun sendFriendRequest(toUser: User) {
        viewModelScope.launch {
            repo.sendFriendRequest(currentUser, toUser.uid)
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            repo.acceptFriendRequest(request, currentUser)
        }
    }

    fun declineFriendRequest(requestId: String) {
        viewModelScope.launch {
            repo.declineFriendRequest(requestId)
        }
    }

    fun saveExpense(
        title: String,
        lineItems: List<LineItem>,
        participants: List<User>
    ) {
        viewModelScope.launch {
            val uid = currentUser.uid
            val allParticipantUids = (participants.map { it.uid } + uid).distinct()
            val total = lineItems.sumOf { it.price }

            val expense = Expense(
                title = title,
                paidBy = uid,
                paidByName = currentUser.displayName,
                participants = allParticipantUids,
                lineItems = lineItems.map { item ->
                    mapOf(
                        "name" to item.name,
                        "price" to item.price,
                        "assignees" to item.assignees
                    )
                },
                totalAmount = total,
                timestamp = System.currentTimeMillis()
            )
            repo.addExpense(expense)
        }
    }

    fun updateExpense(
        expenseId: String,
        title: String,
        lineItems: List<LineItem>,
        participants: List<User>
    ) {
        viewModelScope.launch {
            val uid = currentUser.uid
            val allParticipantUids = (participants.map { it.uid } + uid).distinct()
            val total = lineItems.sumOf { it.price }

            val expense = Expense(
                id = expenseId,
                title = title,
                paidBy = uid,
                paidByName = currentUser.displayName,
                participants = allParticipantUids,
                lineItems = lineItems.map { item ->
                    mapOf(
                        "name" to item.name,
                        "price" to item.price,
                        "assignees" to item.assignees
                    )
                },
                totalAmount = total,
                timestamp = System.currentTimeMillis()
            )
            repo.updateExpense(expense)
        }
    }

    fun markSettled(expenseId: String) {
        viewModelScope.launch {
            repo.markExpenseSettled(expenseId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        expenseListener?.remove()
        friendListener?.remove()
        requestListener?.remove()
    }
}
