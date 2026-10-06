package io.github.danyk20.cardholder.core.domain.validation

enum class ValidationError {
    REQUIRED,
    INVALID_CHARACTERS,
    INVALID_LENGTH,
    INVALID_CHECKSUM,
    INVALID_DATE,
}

enum class CardField {
    TITLE,
    NUMBER,
    EXPIRY,
    HOLDER,
    CVV,
    DOCUMENT_NUMBER,
    SHOP,
    CODE,
}
