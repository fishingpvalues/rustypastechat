package com.rustypastechat.util

object FuzzySearch {
    fun search(query: String, corpus: String): Boolean {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return true
        val c = corpus.lowercase()

        // Exact match
        if (c.contains(q)) return true

        // Fuzzy: check if all query chars appear in order (subsequence match)
        if (isSubsequence(q, c)) return true

        // Trigram similarity
        val qTrigrams = trigrams(q)
        val cTrigrams = trigrams(c)
        if (qTrigrams.isEmpty()) return false
        val intersection = qTrigrams.count { it in cTrigrams }
        return intersection.toDouble() / qTrigrams.size >= 0.5
    }

    private fun isSubsequence(query: String, corpus: String): Boolean {
        var qi = 0
        for (ch in corpus) {
            if (qi < query.length && ch == query[qi]) qi++
            if (qi >= query.length) return true
        }
        return false
    }

    private fun trigrams(s: String): Set<String> {
        val padded = "  $s "
        return (0 until padded.length - 2).map { padded.substring(it, it + 3) }.toSet()
    }
}
