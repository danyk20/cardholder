package io.github.danyk20.cardholder.core.domain.model

import io.github.danyk20.cardholder.core.model.CardId
import io.github.danyk20.cardholder.core.model.CardType

enum class ImportStrategy {
    /** Cards that already exist on this device are left untouched. */
    SKIP_EXISTING,

    /** Cards that already exist on this device are overwritten with the backup version. */
    REPLACE_EXISTING,
}

sealed interface BackupResult {
    data class Exported(val cardCount: Int) : BackupResult

    data class Imported(val imported: Int, val skipped: Int) : BackupResult

    data object WrongPassword : BackupResult

    /** The file is encrypted, but no password was given. */
    data object PasswordRequired : BackupResult

    data object InvalidFile : BackupResult

    data object AuthenticationRequired : BackupResult

    data object KeyInvalidated : BackupResult

    /** The backup contains CVVs or locked cards, which need a screen lock on this device. */
    data object DeviceNotSecure : BackupResult

    data class Failed(val cause: Throwable) : BackupResult
}

/** A card found in a shared file, before the receiver decided what to do with it. */
data class SharedCardPreview(
    /** Identifies the card within the file. */
    val key: String,
    val title: String,
    val type: CardType,
    /** Whether the sender had the card locked; only a suggestion for the receiver. */
    val wasLocked: Boolean,
    /** The card on this device the shared one duplicates (same card or same number/code), if any. */
    val duplicateOf: CardId? = null,
)

/** What to do with one shared card. */
enum class SharedCardAction {
    /** Add it as a separate card, even when it duplicates an existing one. */
    ADD_NEW,

    /** Overwrite the existing card it duplicates. */
    REPLACE,
    SKIP,
}

data class SharedCardChoice(val action: SharedCardAction, val locked: Boolean)

sealed interface SharedFilePreview {
    data class Cards(val cards: List<SharedCardPreview>) : SharedFilePreview

    /** The file can't be read; [result] says why (wrong password, invalid file, ...). */
    data class Unreadable(val result: BackupResult) : SharedFilePreview
}
