package com.hazia.medicall.data.backup

/**
 * When backups are due (SPEC:R22), all times in epoch milliseconds. The rules are provisional (plan
 * phase 1, questions 1 and 2): the automatic backup at most once an hour after a change, and at each
 * start of the app; the weekly one 7 days after the last; every weekly file is kept. The integrity
 * check (SPEC:R23) runs before each backup write and on its own once a week ([checkDue]).
 */
class BackupSchedule {

    /**
     * The backups due at [now]: LATEST when none was written yet, at an app start ([starting]), or when
     * something changed since the last one ([changedSince]) and an hour has passed; WEEKLY when none
     * was written yet or 7 days have passed since the last.
     */
    fun due(now: Long, lastLatest: Long?, lastWeekly: Long?, changedSince: Boolean, starting: Boolean = false): Set<BackupKind> = buildSet {
        if (lastLatest == null || starting || (changedSince && now - lastLatest >= HOUR)) add(BackupKind.LATEST)
        if (lastWeekly == null || now - lastWeekly >= WEEK) add(BackupKind.WEEKLY)
    }

    /** Whether the integrity check is due on its own, a week after the last one. */
    fun checkDue(now: Long, lastCheck: Long?): Boolean = lastCheck == null || now - lastCheck >= WEEK

    private companion object {
        const val HOUR = 60 * 60 * 1000L
        const val WEEK = 7 * 24 * HOUR
    }
}
