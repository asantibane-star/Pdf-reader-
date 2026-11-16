package dev.lector.app.ui

import dev.lector.app.core.result.DocError
import dev.lector.app.domain.model.DocumentKind
import dev.lector.app.testing.FakeServices
import dev.lector.app.testing.Fixtures
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
class MainViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private fun services() = FakeServices(TestDispatchers(main.dispatcher), CoroutineScope(main.dispatcher)).also {
        it.source.put("content://x/1", Fixtures.bytes("simple.pdf"))
        it.source.put("content://x/txt", Fixtures.bytes("utf8.txt"))
    }

    @Test fun openingADocumentEmitsTheViewerEventAndRegistersIt() = runTest(main.dispatcher) {
        val s = services()
        val vm = MainViewModel(s)
        vm.open("content://x/1", external = true)
        assertEquals(OpenState.Opening, vm.state.value)
        advanceUntilIdle()
        val event = vm.eventFlow.first() as MainEvent.ShowDocument
        assertEquals(DocumentKind.Pdf, event.kind)
        assertTrue(event.external)
        assertEquals(OpenState.Idle, vm.state.value)
        assertTrue(s.documents.get(event.docId) != null)
    }

    @Test fun aSecondTapWhileOpeningIsIgnored() = runTest(main.dispatcher) {
        val s = services()
        val vm = MainViewModel(s)
        vm.open("content://x/1", external = false)
        vm.open("content://x/1", external = false)
        vm.open("content://x/1", external = false)
        advanceUntilIdle()
        assertEquals("solo una ingesta", 1, s.source.opens)
    }

    @Test fun failuresAreTypedAndCarryTheRecentId() = runTest(main.dispatcher) {
        val vm = MainViewModel(services())
        vm.open("content://x/perdido", external = false, recentId = 7)
        advanceUntilIdle()
        assertEquals(OpenState.Failed(DocError.AccessLost, "content://x/perdido", 7), vm.state.value)
        vm.dismissError()
        assertEquals(OpenState.Idle, vm.state.value)
    }

    @Test fun nonContentSchemesAreRejected() = runTest(main.dispatcher) {
        val vm = MainViewModel(services())
        vm.open("file:///sdcard/a.pdf", external = true)
        advanceUntilIdle()
        assertEquals(DocError.UnsupportedSource, (vm.state.value as OpenState.Failed).error)
    }

    @Test fun findingTheFileReplacesTheStaleRecent() = runTest(main.dispatcher) {
        val s = services()
        val old = s.recents.seed("content://x/viejo", lastPage = 3, pageCount = 9)
        val vm = MainViewModel(s)
        vm.open("content://x/1", external = false, replaces = old.id)
        advanceUntilIdle()
        assertEquals(listOf(old.id), s.recents.removed)
    }

    @Test fun removingFromTheLostAccessDialogDeletesTheRow() = runTest(main.dispatcher) {
        val s = services()
        val row = s.recents.seed("content://x/viejo", 0, 0)
        val vm = MainViewModel(s)
        vm.open("content://x/viejo", external = false, recentId = row.id)
        advanceUntilIdle()
        vm.removeRecent(row.id)
        advanceUntilIdle()
        assertEquals(OpenState.Idle, vm.state.value)
        assertEquals(listOf(row.id), s.recents.removed)
    }
}
