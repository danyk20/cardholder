package io.github.danyk20.cardholder.core.domain.model

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

    data object InvalidFile : BackupResult

    data object AuthenticationRequired : BackupResult

    data object KeyInvalidated : BackupResult

    /** The backup contains CVVs or locked cards, which need a screen lock on this device. */
    data object DeviceNotSecure : BackupResult

    data class Failed(val cause: Throwable) : BackupResult
}
