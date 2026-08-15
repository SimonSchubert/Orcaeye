package com.inspiredandroid.orcaeye.data

/**
 * Lower-kebab identifier used everywhere a user-typed name becomes a filename or a crontab
 * marker: skills, memories, rules, loop names and log files all go through here, so the same
 * input never lands on disk two different ways.
 */
fun slug(value: String): String = value
    .trim()
    .lowercase()
    .replace(Regex("\\s+"), "-")
    .replace(Regex("[^a-z0-9._-]+"), "")
    .trim('-', '.', '_')
