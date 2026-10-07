package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.repository.CardTextScanner

/** Returns the [lines] set for an image URI. */
class FakeCardTextScanner : CardTextScanner {
    val lines = mutableMapOf<String, List<String>>()

    override suspend fun readLines(uri: String): List<String> = lines[uri].orEmpty()
}
