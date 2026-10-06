package io.github.danyk20.cardholder.feature.cardeditor

import io.github.danyk20.cardholder.core.domain.repository.BankRepository
import io.github.danyk20.cardholder.core.domain.repository.CountryRepository
import io.github.danyk20.cardholder.core.domain.repository.ShopRepository
import javax.inject.Inject

/** Reference data offered by the editor's pickers. */
class EditorCatalogues @Inject constructor(
    val shops: ShopRepository,
    val banks: BankRepository,
    val countries: CountryRepository,
)
