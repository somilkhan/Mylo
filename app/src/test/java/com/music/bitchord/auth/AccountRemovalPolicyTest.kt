package com.music.bitchord.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountRemovalPolicyTest {

    @Test
    fun removingInactiveAccountKeepsCurrentSelectionEvenWhenItIsNotFirst() {
        val selected = remainingAccountIdAfterRemoval(
            accountIds = listOf("account-a", "account-b", "account-c"),
            activeAccountId = "account-b",
            removedAccountId = "account-c",
        )

        assertEquals("account-b", selected)
    }

    @Test
    fun removingActiveAccountFallsBackToFirstRemainingAccount() {
        val selected = remainingAccountIdAfterRemoval(
            accountIds = listOf("account-a", "account-b", "account-c"),
            activeAccountId = "account-b",
            removedAccountId = "account-b",
        )

        assertEquals("account-a", selected)
    }

    @Test
    fun removingUnknownAccountDoesNotChangeCurrentSelection() {
        val selected = remainingAccountIdAfterRemoval(
            accountIds = listOf("account-a", "account-b"),
            activeAccountId = "account-b",
            removedAccountId = "missing",
        )

        assertEquals("account-b", selected)
    }

    @Test
    fun removingLastAccountLeavesNoSelection() {
        val selected = remainingAccountIdAfterRemoval(
            accountIds = listOf("account-a"),
            activeAccountId = "account-a",
            removedAccountId = "account-a",
        )

        assertEquals(null, selected)
    }
}
