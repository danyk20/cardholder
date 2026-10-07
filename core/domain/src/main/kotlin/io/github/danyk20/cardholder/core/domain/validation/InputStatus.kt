package io.github.danyk20.cardholder.core.domain.validation

/** Result of checking a field while the user is still typing. */
enum class InputStatus {
    /** Not enough input yet to tell. */
    INCOMPLETE,
    VALID,

    /** Can't become valid by typing more. */
    INVALID,
}
