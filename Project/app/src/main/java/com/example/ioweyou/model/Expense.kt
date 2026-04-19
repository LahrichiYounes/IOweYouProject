package com.example.ioweyou.model

data class Expense(
    val id: String = "",
    val title: String = "",
    val paidBy: String = "",
    val paidByName: String = "",
    val participants: List<String> = emptyList(),
    val lineItems: List<Map<String, Any>> = emptyList(),
    val totalAmount: Double = 0.0,
    val timestamp: Long = 0L,
    val settled: Boolean = false
) {
    fun getParsedLineItems(): List<LineItem> {
        return lineItems.map { map ->
            LineItem(
                name = map["name"] as? String ?: "",
                price = (map["price"] as? Number)?.toDouble() ?: 0.0,
                assignees = (map["assignees"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            )
        }
    }

    fun owedBy(uid: String): Double {
        if (uid == paidBy) return 0.0
        var total = 0.0
        for (item in getParsedLineItems()) {
            if (item.assignees.contains(uid)) {
                total += item.price / item.assignees.size
            }
        }
        return total
    }

    fun owedTo(uid: String): Double {
        if (uid != paidBy) return 0.0
        return getParsedLineItems().sumOf { item ->
            item.assignees.filter { it != uid }.sumOf { item.price / item.assignees.size }
        }
    }
}
