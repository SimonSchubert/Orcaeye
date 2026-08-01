package com.inspiredandroid.orcaeye.data

import com.inspiredandroid.orcaeye.model.LoopJob
import com.inspiredandroid.orcaeye.model.LoopSnapshot
import com.inspiredandroid.orcaeye.model.ToolKind

/**
 * Reads and writes the user's crontab. Every mutating call backs the crontab up first and
 * preserves lines Orcaeye did not create.
 */
interface CrontabRepository {
    suspend fun loadJobs(): LoopSnapshot

    /** Creates or replaces [job]. An adopted job is promoted to managed by the caller. */
    suspend fun saveJob(job: LoopJob)

    suspend fun deleteJob(id: String)

    /** Runs [job]'s command right now in a terminal window, without touching the schedule. */
    suspend fun runNow(job: LoopJob)

    /** Where a newly created job should append its output. */
    fun defaultLogPathFor(name: String): String

    /** The `PATH=` value new jobs get, so cron finds the CLI and the tools it shells out to. */
    fun defaultPathPrefix(): String

    /** Absolute path to [tool]'s binary, or its bare name when it isn't installed. */
    fun binaryFor(tool: ToolKind): String
}
