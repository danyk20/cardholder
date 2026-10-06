package io.github.danyk20.cardholder.core.testing.repository

import io.github.danyk20.cardholder.core.domain.repository.LogoDownloader

/** Serves logos from [logos]; unknown URLs fail like a network error. */
class FakeLogoDownloader(val logos: MutableMap<String, ByteArray> = mutableMapOf()) : LogoDownloader {
    val requested = mutableListOf<String>()

    override suspend fun download(url: String): ByteArray? {
        requested += url
        return logos[url]
    }
}
