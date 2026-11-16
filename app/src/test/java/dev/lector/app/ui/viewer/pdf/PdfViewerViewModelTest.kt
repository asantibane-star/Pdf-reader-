package dev.lector.app.ui.viewer.pdf

import androidx.lifecycle.SavedStateHandle
import dev.lector.app.core.result.DocError
import dev.lector.app.domain.model.DocumentRef
import dev.lector.app.testing.FakePdfEngine
import dev.lector.app.testing.FakeServices
import dev.lector.app.testing.Fixtures
import dev.lector.app.testing.MainDispatcherRule
import dev.lector.app.testing.TestDispatchers
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PdfViewerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private fun services(engine: FakePdfEngine = FakePdfEngine()) =
        FakeServices(TestDispatchers(main.dispatcher), CoroutineScope(main.dispatcher), pdfEngine = engine).also {
            it.source.put("content://x/1", Fixtures.bytes("simple.pdf"))
        }

    /** Ingiere un documento como lo haría `MainViewModel` y devuelve su referencia ya registrada. */
    private suspend fun ingest(s: FakeServices): DocumentRef {
        val ref = (s.uriIntake.copyToCache("content://x/1") as dev.lector.app.core.result.Outcome.Success).value
        s.documents.register(ref)
        return ref
    }

    private fun TestScope.viewModel(s: FakeServices, state: Map<String, Any?>): PdfViewerViewModel {
        val vm = PdfViewerViewModel(SavedStateHandle(state), s)
        advanceUntilIdle()
        return vm
    }

    @Test fun opensTheRegisteredDocument() = runTest(main.dispatcher) {
        val s = services()
        val ref = ingest(s)
        val vm = viewModel(s, mapOf("docId" to ref.id))
        val content = vm.state.value as PdfUiState.Content
        assertEquals(20, content.pageCount)
        assertEquals("Documento", content.title)
        assertEquals(0, content.initialPage)
        assertEquals("se registra en recientes", 1, s.recents.observe().first().size)
    }

    @Test fun resumesAtTheLastPageStoredInRecents() = runTest(main.dispatcher) {
        val s = services()
        s.recents.seed("content://x/1", lastPage = 12, pageCount = 20)
        val ref = ingest(s)
        val vm = viewModel(s, mapOf("docId" to ref.id))
        assertEquals(12, (vm.state.value as PdfUiState.Content).initialPage)
    }

    @Test fun restoresAfterProcessDeathFromSavedState() = runTest(main.dispatcher) {
        val s = services() // registro vacío y caché sin copia: como tras la muerte del proceso
        val vm = viewModel(s, mapOf("docId" to "ya-no-existe", "sourceUri" to "content://x/1", "page" to 7, "zoom" to 2.5f, "night" to true))
        val content = vm.state.value as PdfUiState.Content
        assertEquals("reingesta desde sourceUri", 7, content.initialPage)
        assertEquals(2.5f, content.initialZoom)
        assertTrue(vm.night.value)
        assertEquals(1, s.source.opens)
    }

    @Test fun lostAccessAfterProcessDeathIsAnErrorWithAWayOut() = runTest(main.dispatcher) {
        val s = services()
        val vm = viewModel(s, mapOf("docId" to "x", "sourceUri" to "content://x/revocado"))
        assertEquals(PdfUiState.Error(DocError.AccessLost, passwordSupported = true), vm.state.value)
    }

    @Test fun missingSourceAndRegistryIsAccessLost() = runTest(main.dispatcher) {
        val vm = viewModel(services(), mapOf("docId" to "x"))
        assertEquals(DocError.AccessLost, (vm.state.value as PdfUiState.Error).error)
    }

    @Test fun passwordFlowAsksThenOpens() = runTest(main.dispatcher) {
        val engine = FakePdfEngine(requiredPassword = "test")
        val s = services(engine)
        val vm = viewModel(s, mapOf("docId" to ingest(s).id))
        assertEquals(PdfUiState.PasswordNeeded(wrong = false), vm.state.value)
        vm.submitPassword("nope")
        advanceUntilIdle()
        assertEquals(PdfUiState.PasswordNeeded(wrong = true), vm.state.value)
        vm.submitPassword("test")
        advanceUntilIdle()
        assertTrue(vm.state.value is PdfUiState.Content)
        assertEquals(listOf<String?>(null, "nope", "test"), engine.opens)
    }

    @Test fun passwordOnAndroidWithoutSupportIsAnExplainedError() = runTest(main.dispatcher) {
        val s = services(FakePdfEngine(supportsPasswordValue = false, requiredPassword = "x"))
        val vm = viewModel(s, mapOf("docId" to ingest(s).id))
        assertEquals(PdfUiState.Error(DocError.PasswordRequired, passwordSupported = false), vm.state.value)
    }

    @Test fun corruptFilesEndInAnError() = runTest(main.dispatcher) {
        val s = services(FakePdfEngine(failure = DocError.Corrupt))
        val vm = viewModel(s, mapOf("docId" to ingest(s).id))
        assertEquals(DocError.Corrupt, (vm.state.value as PdfUiState.Error).error)
    }

    @Test fun shareIsProtectedAgainstDoubleTap() = runTest(main.dispatcher) {
        val s = services()
        val vm = viewModel(s, mapOf("docId" to ingest(s).id))
        vm.share()
        vm.share()
        vm.share()
        advanceUntilIdle()
        assertEquals(1, s.exporter.shares)
        assertFalse(vm.busy.value)
        assertTrue(vm.effectFlow.first() is PdfEffect.Share)
    }

    @Test fun pageJumpsAreClampedAndPersistedWithDebounce() = runTest(main.dispatcher) {
        val s = services()
        val vm = viewModel(s, mapOf("docId" to ingest(s).id))
        vm.requestPage(999)
        assertEquals(PdfEffect.ScrollToPage(19, 0f), vm.effectFlow.first())
        assertEquals(19, vm.page.value)
        s.recents.progress.clear()
        advanceTimeBy(PdfViewerViewModel.PROGRESS_DEBOUNCE_MS + 10)
        assertTrue("el progreso se guarda tras 1 s", s.recents.progress.any { it.second == 19 && it.third == 20 })
    }

    @Test fun closingTheViewerClosesTheSessionAndDeletesTheCacheCopy() = runTest(main.dispatcher) {
        val engine = FakePdfEngine()
        val s = services(engine)
        val ref = ingest(s)
        val vm = viewModel(s, mapOf("docId" to ref.id))
        assertTrue(File(ref.cachePath).isFile)
        val onCleared = PdfViewerViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }
        onCleared.invoke(vm)
        advanceUntilIdle()
        assertTrue(engine.sessions.single().closed)
        assertFalse(File(ref.cachePath).exists())
        assertEquals(null, s.documents.get(ref.id))
    }
}
