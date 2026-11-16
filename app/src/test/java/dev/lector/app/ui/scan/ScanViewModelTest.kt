package dev.lector.app.ui.scan

import androidx.lifecycle.SavedStateHandle
import dev.lector.app.core.result.DocError
import dev.lector.app.core.result.Outcome
import dev.lector.app.domain.model.PendingScan
import dev.lector.app.testing.FakeScans
import dev.lector.app.testing.FakeServices
import dev.lector.app.testing.MainDispatcherRule
import dev.lector.app.testing.TestDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScanViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private fun services() = FakeServices(TestDispatchers(main.dispatcher), CoroutineScope(main.dispatcher))
    private val scans: (FakeServices) -> FakeScans = { it.scans as FakeScans }

    private fun kotlinx.coroutines.test.TestScope.vm(s: FakeServices, saved: Map<String, Any?> = emptyMap()): ScanViewModel {
        val viewModel = ScanViewModel(SavedStateHandle(saved), s)
        advanceUntilIdle()
        return viewModel
    }

    @Test fun scannerResultLeadsToTheNamingSheetWithADefaultName() = runTest(main.dispatcher) {
        val s = services()
        val vm = vm(s)
        vm.onScannerResult("content://mlkit/temp.pdf", pageCount = 3)
        advanceUntilIdle()
        val naming = vm.state.value as ScanState.Naming
        assertEquals(3, naming.pending.pageCount)
        assertTrue(Regex("""Escaneo \d{4}-\d{2}-\d{2} \d{4}""").matches(naming.suggestedName))
    }

    @Test fun savingSanitizesTheNameDiscardsThePendingAndEmitsSaved() = runTest(main.dispatcher) {
        val s = services()
        val vm = vm(s)
        vm.onScannerResult("content://mlkit/temp.pdf", 2)
        advanceUntilIdle()
        vm.save("Factura/2026: <final>")
        advanceUntilIdle()
        val event = vm.eventFlow.first() as ScanEvent.Saved
        assertEquals("Factura2026 final", event.name)
        assertEquals(listOf("p-1" to "Factura2026 final"), scans(s).saved)
        assertEquals(listOf("p-1"), scans(s).discarded)
        assertEquals(ScanState.Idle, vm.state.value)
    }

    @Test fun aFailedSaveKeepsThePendingAndCanBeRetried() = runTest(main.dispatcher) {
        val s = services()
        val vm = vm(s)
        vm.onScannerResult("content://mlkit/temp.pdf", 1)
        advanceUntilIdle()
        scans(s).saveResult = Outcome.Failure(DocError.SaveFailed)
        vm.save("Nombre")
        advanceUntilIdle()
        val failed = vm.state.value as ScanState.SaveFailed
        assertEquals(DocError.SaveFailed, failed.error)
        assertTrue("el pendiente se conserva", scans(s).discarded.isEmpty())
        scans(s).saveResult = Outcome.Success("content://media/external_primary/file/9")
        vm.save("Nombre")
        advanceUntilIdle()
        assertEquals("content://media/external_primary/file/9", (vm.eventFlow.first() as ScanEvent.Saved).uri)
        assertEquals(listOf("p-1"), scans(s).discarded)
    }

    @Test fun doubleTapOnSaveSavesOnce() = runTest(main.dispatcher) {
        val s = services()
        val vm = vm(s)
        vm.onScannerResult("content://mlkit/temp.pdf", 1)
        advanceUntilIdle()
        vm.save("A")
        vm.save("A")
        vm.save("A")
        advanceUntilIdle()
        assertEquals(1, scans(s).saveCalls)
    }

    @Test fun discardingDeletesThePendingFile() = runTest(main.dispatcher) {
        val s = services()
        val vm = vm(s)
        vm.onScannerResult("content://mlkit/temp.pdf", 1)
        advanceUntilIdle()
        vm.discard()
        advanceUntilIdle()
        assertEquals(ScanState.Idle, vm.state.value)
        assertEquals(listOf("p-1"), scans(s).discarded)
    }

    @Test fun anInterruptedScanResumesAfterProcessDeath() = runTest(main.dispatcher) {
        val s = services()
        scans(s).pendingList += PendingScan("abc", "/tmp/abc.pdf", 5)
        val vm = vm(s, mapOf(ScanViewModel.KEY_PENDING_ID to "abc"))
        assertEquals(5, (vm.state.value as ScanState.Naming).pending.pageCount)
    }

    @Test fun anOrphanPendingIsOfferedAtStartup() = runTest(main.dispatcher) {
        val s = services()
        scans(s).pendingList += PendingScan("huerfano", "/tmp/h.pdf", 2)
        val vm = vm(s)
        assertEquals(PendingScan("huerfano", "/tmp/h.pdf", 2), (vm.state.value as ScanState.OrphanPrompt).pending)
        vm.acceptOrphan()
        advanceUntilIdle()
        assertTrue(vm.state.value is ScanState.Naming)
    }

    @Test fun discardingAnOrphanDeletesIt() = runTest(main.dispatcher) {
        val s = services()
        scans(s).pendingList += PendingScan("huerfano", "/tmp/h.pdf", 2)
        val vm = vm(s)
        vm.discard()
        advanceUntilIdle()
        assertEquals(ScanState.Idle, vm.state.value)
        assertEquals(listOf("huerfano"), scans(s).discarded)
    }

    @Test fun imagesBecomeAPdfWithProgress() = runTest(main.dispatcher) {
        val s = services()
        val vm = vm(s)
        vm.createFromImages(emptyList())
        assertEquals(ScanState.Idle, vm.state.value)
        vm.createFromImages(listOf("/a.jpg", "/b.jpg", "/c.jpg"))
        advanceUntilIdle()
        assertEquals(3, (vm.state.value as ScanState.Naming).pending.pageCount)
    }
}
