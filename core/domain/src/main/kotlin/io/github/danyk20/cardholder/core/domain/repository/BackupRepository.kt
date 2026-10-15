package io.github.danyk20.cardholder.core.domain.repository

import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.ImportStrategy
import io.github.danyk20.cardholder.core.domain.model.SharedCardChoice
import io.github.danyk20.cardholder.core.domain.model.SharedFilePreview
import io.github.danyk20.cardholder.core.model.CardId

interface BackupRepository {
    /** Writes all cards, encrypted with [password], to the document at [destinationUri]. Requires authentication. */
    suspend fun export(destinationUri: String, password: CharArray): BackupResult

    /**
     * Writes only the cards [ids] (including CVVs and notes) to [destinationUri] for sharing. Encrypted when a
     * [password] is given, otherwise readable by anyone who gets the file. Protected cards need authentication.
     */
    suspend fun exportCards(ids: Collection<CardId>, destinationUri: String, password: CharArray?): BackupResult

    /**
     * Imports a backup or shared file. [password] may be `null` for an unencrypted shared file; an encrypted
     * file then reports [BackupResult.PasswordRequired].
     */
    suspend fun import(sourceUri: String, password: CharArray?, strategy: ImportStrategy): BackupResult

    /** Lists the cards of a shared (or backup) file and which of them already exist on this device. */
    suspend fun preview(sourceUri: String, password: CharArray?): SharedFilePreview

    /** Imports the cards of [sourceUri] as the receiver chose; cards without a choice are skipped. */
    suspend fun importShared(
        sourceUri: String,
        password: CharArray?,
        choices: Map<String, SharedCardChoice>,
    ): BackupResult
}
