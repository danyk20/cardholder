package io.github.danyk20.cardholder.core.security

/** How a piece of data is protected on top of the at-rest encryption. */
enum class ProtectionLevel(internal val id: Byte) {
    /** Encrypted with a hardware-backed key that is usable without user interaction. */
    STANDARD(1),

    /**
     * Encrypted so that decryption needs a hardware-backed key that is only usable for a short
     * time after the user authenticated with a strong biometric or the device credential.
     * Encryption itself never requires authentication.
     */
    PROTECTED(2),
    ;

    internal companion object {
        fun fromId(id: Byte): ProtectionLevel =
            entries.firstOrNull { it.id == id } ?: throw CorruptedDataException("Unknown protection level $id")
    }
}
