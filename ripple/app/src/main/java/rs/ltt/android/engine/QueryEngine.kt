/*
 * Copyright 2026 Ripple contributors
 * Licensed under the Apache License, Version 2.0
 */
package rs.ltt.android.engine

import rs.ltt.android.database.LttrsDatabase
import rs.ltt.android.database.dao.EngineDao
import rs.ltt.android.entity.EmailAddressType
import rs.ltt.android.mail.model.Email
import rs.ltt.android.mail.model.filter.EmailFilterCondition
import rs.ltt.android.mail.model.filter.Filter
import rs.ltt.android.mail.model.filter.FilterOperator
import rs.ltt.android.mail.model.filter.Operator
import rs.ltt.android.mail.model.query.EmailQuery

/**
 * Answers Ltt.rs' email queries from the local database. JMAP servers compute these; with IMAP we
 * evaluate the filter over what has been synchronized and store the (thread collapsed) result in
 * the query tables the UI pages through.
 */
class QueryEngine(database: LttrsDatabase) {

    private val dao = database.engineDao()

    fun materialize(query: EmailQuery) {
        allIdsCache = null
        val candidates = candidates(query.filter)
        val matching = evaluate(query.filter)
        val filtered = if (matching == null) candidates else candidates.filter { it.emailId in matching }
        val sorted = filtered.sortedWith(
            compareByDescending<EngineDao.QueryCandidate> { it.receivedAt }.thenByDescending { it.emailId },
        )
        val items =
            if (query.collapseThreads == true) {
                val seen = HashSet<String>()
                sorted.filter { seen.add(it.threadId) }
            } else {
                sorted
            }
        dao.setQueryResult(query.asHash(), items.map { EngineDao.QueryItem(it.emailId, it.threadId) })
    }

    /** The folder a query is about, if it is a plain folder listing. */
    fun mailboxOf(query: EmailQuery): String? = (query.filter as? EmailFilterCondition)?.inMailbox

    private fun candidates(filter: Filter<Email>?): List<EngineDao.QueryCandidate> {
        val mailbox = (filter as? EmailFilterCondition)?.inMailbox
        return if (mailbox != null) dao.getCandidates(mailbox) else dao.getAllCandidates()
    }

    /** @return the matching email ids or null when everything matches */
    private fun evaluate(filter: Filter<Email>?): Set<String>? {
        return when (filter) {
            null -> null
            is EmailFilterCondition -> evaluate(filter)
            is FilterOperator<Email> -> {
                val parts = filter.conditions.map { evaluate(it) }
                when (filter.operator) {
                    Operator.AND -> parts.fold(null as Set<String>?) { acc, set -> intersect(acc, set) }
                    Operator.OR ->
                        if (parts.any { it == null }) null else parts.flatMap { it!! }.toSet()
                    Operator.NOT -> {
                        val excluded = if (parts.any { it == null }) return emptySet() else parts.flatMap { it!! }.toSet()
                        allIds() - excluded
                    }
                    null -> null
                }
            }
            else -> null
        }
    }

    private fun evaluate(condition: EmailFilterCondition): Set<String>? {
        var result: Set<String>? = null
        condition.inMailbox?.let { result = intersect(result, dao.getEmailIdsInMailboxes(listOf(it)).toSet()) }
        condition.inMailboxOtherThan?.takeIf { it.isNotEmpty() }?.let {
            result = intersect(result, allIds() - dao.getEmailIdsInMailboxes(it.toList()).toSet())
        }
        condition.hasKeyword?.let { result = intersect(result, dao.getEmailIdsWithKeyword(it).toSet()) }
        condition.notKeyword?.let { result = intersect(result, allIds() - dao.getEmailIdsWithKeyword(it).toSet()) }
        condition.someInThreadHaveKeyword?.let { result = intersect(result, inThreadsOf(dao.getEmailIdsWithKeyword(it).toSet())) }
        condition.noneInThreadHaveKeyword?.let {
            result = intersect(result, allIds() - inThreadsOf(dao.getEmailIdsWithKeyword(it).toSet()))
        }
        condition.allInThreadHaveKeyword?.let {
            val without = allIds() - dao.getEmailIdsWithKeyword(it).toSet()
            result = intersect(result, allIds() - inThreadsOf(without))
        }
        condition.text?.let { result = intersect(result, dao.getEmailIdsByText(like(it)).toSet()) }
        condition.subject?.let { result = intersect(result, dao.getEmailIdsBySubject(like(it)).toSet()) }
        condition.body?.let { result = intersect(result, dao.getEmailIdsByBody(like(it)).toSet()) }
        condition.from?.let { result = intersect(result, dao.getEmailIdsByAddress(EmailAddressType.FROM.name, like(it)).toSet()) }
        condition.to?.let { result = intersect(result, dao.getEmailIdsByAddress(EmailAddressType.TO.name, like(it)).toSet()) }
        condition.cc?.let { result = intersect(result, dao.getEmailIdsByAddress(EmailAddressType.CC.name, like(it)).toSet()) }
        condition.bcc?.let { result = intersect(result, dao.getEmailIdsByAddress(EmailAddressType.BCC.name, like(it)).toSet()) }
        condition.hasAttachment?.let {
            val with = dao.getEmailIdsWithAttachment().toSet()
            result = intersect(result, if (it) with else allIds() - with)
        }
        val before = condition.before
        val after = condition.after
        if (before != null || after != null) {
            result = intersect(
                result,
                dao.getAllCandidates()
                    .filter { c ->
                        val t = c.receivedAt
                        t != null && (before == null || t.isBefore(before)) && (after == null || !t.isBefore(after))
                    }
                    .map { it.emailId }
                    .toSet(),
            )
        }
        return result
    }

    private var allIdsCache: Set<String>? = null

    private fun allIds(): Set<String> =
        allIdsCache ?: dao.getAllCandidates().map { it.emailId }.toSet().also { allIdsCache = it }

    private fun inThreadsOf(emailIds: Set<String>): Set<String> {
        val all = dao.getAllCandidates()
        val threads = all.filter { it.emailId in emailIds }.map { it.threadId }.toSet()
        return all.filter { it.threadId in threads }.map { it.emailId }.toSet()
    }

    private fun intersect(a: Set<String>?, b: Set<String>?): Set<String>? =
        when {
            a == null -> b
            b == null -> a
            else -> a intersect b
        }

    private fun like(value: String): String =
        "%" + value.replace("\\", "").replace("%", "").replace("_", " ").trim() + "%"
}
