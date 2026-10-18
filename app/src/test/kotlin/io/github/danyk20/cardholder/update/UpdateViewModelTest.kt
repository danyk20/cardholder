package io.github.danyk20.cardholder.update

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.danyk20.cardholder.core.testing.MainDispatcherRule
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class UpdateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var now = Instant.parse("2026-10-18T10:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId) = this
    }
    private val preferences = UpdatePreferences(ApplicationProvider.getApplicationContext(), clock)
    private val source = FakeUpdateSource()
    private val activity = Robolectric.buildActivity(Activity::class.java).get()

    private fun viewModel() = UpdateViewModel(source, preferences)

    @Test
    fun `checking shows the result and remembers when`() {
        val viewModel = viewModel()

        viewModel.check()

        assertEquals(UpdateStatus.UpToDate, viewModel.uiState.value.status)
        assertEquals(now, viewModel.uiState.value.lastCheck)

        source.result = UpdateCheck.Failed
        viewModel.check()
        assertEquals(UpdateStatus.CheckFailed, viewModel.uiState.value.status)
    }

    @Test
    fun `an available update is downloaded and installed`() {
        source.result = UpdateCheck.Available("1.4.0", "Notes")
        val viewModel = viewModel()
        viewModel.check()

        viewModel.install(activity)

        assertEquals(listOf(0.5f, 1f), source.reportedProgress)
        assertEquals(UpdateStatus.Installing, viewModel.uiState.value.status)
    }

    @Test
    fun `without the install permission the update is kept for after allowing it`() {
        source.result = UpdateCheck.Available("1.4.0", null)
        source.installResult = InstallResult.NeedsPermission
        val viewModel = viewModel()
        viewModel.check()

        viewModel.install(activity)

        assertEquals(
            UpdateStatus.NeedsPermission(UpdateCheck.Available("1.4.0", null)),
            viewModel.uiState.value.status,
        )
        source.installResult = InstallResult.Started
        viewModel.install(activity)
        assertEquals(UpdateStatus.Installing, viewModel.uiState.value.status)
    }

    @Test
    fun `automatic checks are off by default, then weekly`() {
        source.result = UpdateCheck.Available("1.4.0", null)
        viewModel().checkIfDue()
        assertEquals(0, source.checks)

        preferences.autoCheck = true
        val viewModel = viewModel()
        viewModel.checkIfDue()
        assertEquals(1, source.checks)
        assertTrue(viewModel.uiState.value.status is UpdateStatus.Available)

        now += Duration.ofDays(6)
        viewModel().checkIfDue()
        assertEquals(1, source.checks)

        now += Duration.ofDays(1)
        viewModel().checkIfDue()
        assertEquals(2, source.checks)
    }

    @Test
    fun `an automatic check only shows something when there is an update`() {
        preferences.autoCheck = true
        source.result = UpdateCheck.Failed
        val viewModel = viewModel()

        viewModel.checkIfDue()

        assertEquals(UpdateStatus.Idle, viewModel.uiState.value.status)
        assertFalse(preferences.lastCheck != null, "a failed check isn't remembered, so it's retried")
    }

    @Test
    fun `a cancelled install offers the update again, a rejected one says it failed`() {
        source.result = UpdateCheck.Available("1.4.0", null)
        val viewModel = viewModel()
        viewModel.check()

        viewModel.install(activity)
        source.unfinished.tryEmit(InstallOutcome.CANCELLED)
        assertEquals(UpdateStatus.Available(UpdateCheck.Available("1.4.0", null)), viewModel.uiState.value.status)

        viewModel.install(activity)
        source.unfinished.tryEmit(InstallOutcome.FAILED)
        assertEquals(UpdateStatus.InstallFailed, viewModel.uiState.value.status)
    }

    private class FakeUpdateSource : UpdateSource {
        var result: UpdateCheck = UpdateCheck.UpToDate
        var installResult: InstallResult = InstallResult.Started
        var checks = 0
        val reportedProgress = mutableListOf<Float>()

        val unfinished = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 1)

        override val channel = UpdateChannel.GITHUB

        override val unfinishedInstalls = unfinished

        override suspend fun check(): UpdateCheck = result.also { checks++ }

        override suspend fun install(activity: Activity, onProgress: (Float) -> Unit): InstallResult {
            listOf(0.5f, 1f).forEach {
                reportedProgress += it
                onProgress(it)
            }
            return installResult
        }
    }
}
