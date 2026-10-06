package io.github.danyk20.cardholder.feature.settings

import io.github.danyk20.cardholder.core.domain.model.BackupResult
import io.github.danyk20.cardholder.core.domain.model.ImportStrategy
import io.github.danyk20.cardholder.core.domain.repository.BackupRepository
import io.github.danyk20.cardholder.core.model.ThemeMode
import io.github.danyk20.cardholder.core.testing.MainDispatcherRule
import io.github.danyk20.cardholder.core.testing.repository.FakeUserPreferencesRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val preferences = FakeUserPreferencesRepository()
    private val backups = RecordingBackupRepository()
    private val viewModel by lazy { SettingsViewModel(preferences, backups) }

    private fun TestScope.observeState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    @Test
    fun `changes appearance preferences`() = runTest {
        observeState()

        viewModel.onThemeModeChange(ThemeMode.DARK)
        viewModel.onDynamicColorChange(false)

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.preferences.themeMode)
        assertEquals(false, viewModel.uiState.value.preferences.useDynamicColor)
    }

    @Test
    fun `export reports the result and wipes the password`() = runTest {
        observeState()
        val password = "a good password".toCharArray()

        viewModel.export("content://backup", password)

        assertEquals(BackupResult.Exported(2), viewModel.uiState.value.backup.result)
        assertEquals("content://backup" to "a good password", backups.exports.single())
        assertTrue(password.all { it == ' ' })

        viewModel.onResultShown()
        assertNull(viewModel.uiState.value.backup.result)
    }

    @Test
    fun `import passes the chosen strategy`() = runTest {
        viewModel.import("content://backup", "pw".toCharArray(), replaceExisting = true)

        assertEquals(ImportStrategy.REPLACE_EXISTING, backups.imports.single())
    }

    private class RecordingBackupRepository : BackupRepository {
        val exports = mutableListOf<Pair<String, String>>()
        val imports = mutableListOf<ImportStrategy>()

        override suspend fun export(destinationUri: String, password: CharArray): BackupResult {
            exports += destinationUri to password.concatToString()
            return BackupResult.Exported(2)
        }

        override suspend fun import(sourceUri: String, password: CharArray, strategy: ImportStrategy): BackupResult {
            imports += strategy
            return BackupResult.Imported(1, 0)
        }
    }
}
