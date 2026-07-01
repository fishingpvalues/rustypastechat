package com.rustypastechat.util

import org.junit.Assert.*
import org.junit.Test

class FuzzySearchTest {

    @Test
    fun `exact match returns true`() {
        assertTrue(FuzzySearch.search("hello", "hello world"))
    }

    @Test
    fun `case insensitive match`() {
        assertTrue(FuzzySearch.search("HELLO", "Hello World"))
    }

    @Test
    fun `subsequence match - scattered characters`() {
        assertTrue(FuzzySearch.search("hlo", "hello"))
        assertTrue(FuzzySearch.search("hll", "hello"))
    }

    @Test
    fun `subsequence match - beginning`() {
        assertTrue(FuzzySearch.search("hel", "hello world"))
    }

    @Test
    fun `subsequence match - middle`() {
        assertTrue(FuzzySearch.search("wor", "hello world"))
    }

    @Test
    fun `no match returns false`() {
        assertFalse(FuzzySearch.search("xyz", "hello world"))
    }

    @Test
    fun `empty query returns true`() {
        assertTrue(FuzzySearch.search("", "anything"))
        assertTrue(FuzzySearch.search("  ", "anything"))
    }

    @Test
    fun `trigram fuzzy match`() {
        // "hllo" has tri-grams that partially overlap with "hello"
        assertTrue(FuzzySearch.search("hllo", "hello"))
    }

    @Test
    fun `trigram fuzzy match with longer text`() {
        assertTrue(FuzzySearch.search("rustypast", "rustypaste chat application"))
        assertTrue(FuzzySearch.search("cht", "rustypaste chat application"))
    }

    @Test
    fun `no fuzzy match for completely different text`() {
        assertFalse(FuzzySearch.search("python", "rustypaste chat"))
        assertFalse(FuzzySearch.search("database", "hello world"))
    }

    @Test
    fun `single character match`() {
        assertTrue(FuzzySearch.search("h", "hello"))
        assertFalse(FuzzySearch.search("z", "hello"))
    }

    @Test
    fun `partial word match via trigrams`() {
        assertTrue(FuzzySearch.search("mesage", "message")) // typo tolerant
        assertTrue(FuzzySearch.search("settngs", "settings")) // typo tolerant
    }

    @Test
    fun `long query no match`() {
        assertFalse(FuzzySearch.search("the quick brown fox", "hello world"))
    }
}
