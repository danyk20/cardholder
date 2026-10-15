package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.ImportStrategy
import io.github.danyk20.cardholder.core.domain.model.SharedCardChoice
import io.github.danyk20.cardholder.core.domain.model.SharedFilePreview
import io.github.danyk20.cardholder.core.domain.repository.BackupRepository
import io.github.danyk20.cardholder.core.domain.repository.ShareFileStore
import io.github.danyk20.cardholder.core.model.CardId

/** Records calls; the password is captured as a string before the caller wipes it. */
class FakeBackupRepository : BackupRepository {
    val exports = mutableListOf<Pair<String, String>>()
    val imports = mutableListOf<ImportStrategy>()
    val sharedExports = mutableListOf<SharedExport>()
    val sharedImports = mutableListOf<Map<String, SharedCardChoice>>()

    /** What [preview] returns for any password; set per test. */
    var preview: (password: String?) -> SharedFilePreview = { SharedFilePreview.Cards(emptyList()) }
    var exportCardsResult: BackupResult? = null

    data class SharedExport(val ids: List<CardId>, val uri: String, val password: String?)

    override suspend fun export(destinationUri: String, password: CharArray): BackupResult {
        exports += destinationUri to password.concatToString()
        return BackupResult.Exported(2)
    }

    override suspend fun exportCards(
        ids: Collection<CardId>,
        destinationUri: String,
        password: CharArray?,
    ): BackupResult {
        sharedExports += SharedExport(ids.toList(), destinationUri, password?.concatToString())
        return exportCardsResult ?: BackupResult.Exported(ids.size)
    }

    override suspend fun import(sourceUri: String, password: CharArray?, strategy: ImportStrategy): BackupResult {
        imports += strategy
        return BackupResult.Imported(1, 0)
    }

    override suspend fun preview(sourceUri: String, password: CharArray?): SharedFilePreview =
        preview(password?.concatToString())

    override suspend fun importShared(
        sourceUri: String,
        password: CharArray?,
        choices: Map<String, SharedCardChoice>,
    ): BackupResult {
        sharedImports += choices
        return BackupResult.Imported(choices.size, 0)
    }
}

class FakeShareFileStore : ShareFileStore {
    var count = 0

    override suspend fun newShareFile(): String = "content://share/${++count}"
}
