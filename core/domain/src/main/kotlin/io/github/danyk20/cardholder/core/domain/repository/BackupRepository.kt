package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.ImportStrategy

interface BackupRepository {
    /** Writes all cards, encrypted with [password], to the document at [destinationUri]. Requires authentication. */
    suspend fun export(destinationUri: String, password: CharArray): BackupResult

    suspend fun import(sourceUri: String, password: CharArray, strategy: ImportStrategy): BackupResult
}
