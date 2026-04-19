package com.example.ioweyou.repository

import com.example.ioweyou.model.Expense
import com.example.ioweyou.model.FriendRequest
import com.example.ioweyou.model.LineItem
import com.example.ioweyou.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val db = FirebaseFirestore.getInstance()

    suspend fun saveUser(user: User) {
        db.collection("users").document(user.uid).set(user).await()
    }

    suspend fun getUser(uid: String): User? {
        return db.collection("users").document(uid).get().await()
            .toObject(User::class.java)
    }

    suspend fun searchUserByEmail(email: String): List<User> {
        val snapshot = db.collection("users")
            .whereEqualTo("email", email)
            .limit(10)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(User::class.java) }
    }

    fun listenToFriends(uid: String, onUpdate: (List<User>) -> Unit): ListenerRegistration {
        return db.collection("users").document(uid).collection("friends")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val friends = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
                onUpdate(friends)
            }
    }

    suspend fun addFriend(currentUid: String, friend: User) {
        db.collection("users").document(currentUid)
            .collection("friends").document(friend.uid).set(friend).await()
    }

    suspend fun sendFriendRequest(from: User, toUid: String) {
        val request = FriendRequest(
            fromUid = from.uid,
            fromName = from.displayName,
            fromEmail = from.email,
            toUid = toUid,
            status = "pending"
        )
        db.collection("friendRequests").add(request).await()
    }

    fun listenToIncomingRequests(uid: String, onUpdate: (List<FriendRequest>) -> Unit): ListenerRegistration {
        return db.collection("friendRequests")
            .whereEqualTo("toUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val requests = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)
                }
                onUpdate(requests)
            }
    }

    suspend fun acceptFriendRequest(request: FriendRequest, currentUser: User) {
        db.collection("friendRequests").document(request.id)
            .update("status", "accepted").await()

        val requester = getUser(request.fromUid) ?: return
        db.collection("users").document(currentUser.uid)
            .collection("friends").document(requester.uid).set(requester).await()
        db.collection("users").document(requester.uid)
            .collection("friends").document(currentUser.uid).set(currentUser).await()
    }

    suspend fun declineFriendRequest(requestId: String) {
        db.collection("friendRequests").document(requestId)
            .update("status", "declined").await()
    }

    fun listenToExpenses(uid: String, onUpdate: (List<Expense>) -> Unit): ListenerRegistration {
        return db.collection("expenses")
            .whereArrayContains("participants", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val expenses = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Expense::class.java)?.copy(id = doc.id)
                }
                onUpdate(expenses)
            }
    }

    suspend fun addExpense(expense: Expense): String {
        val data = hashMapOf(
            "title" to expense.title,
            "paidBy" to expense.paidBy,
            "paidByName" to expense.paidByName,
            "participants" to expense.participants,
            "totalAmount" to expense.totalAmount,
            "timestamp" to expense.timestamp,
            "settled" to expense.settled,
            "lineItems" to expense.getParsedLineItems().map { item ->
                mapOf(
                    "name" to item.name,
                    "price" to item.price,
                    "assignees" to item.assignees
                )
            }
        )
        val ref = db.collection("expenses").add(data).await()
        return ref.id
    }

    suspend fun markExpenseSettled(expenseId: String) {
        db.collection("expenses").document(expenseId)
            .update("settled", true).await()
    }
}
