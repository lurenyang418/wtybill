package tech.wtybill.app.ui.room

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tech.wtybill.app.data.douyu.RoomInfo
import tech.wtybill.app.data.douyu.RoomRepository

@OptIn(ExperimentalCoroutinesApi::class)
class RoomViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    @After fun tearDown() = kotlinx.coroutines.Dispatchers.resetMain()

    @Test fun h5roomFailureDoesNotBlockLiveRoomState() = runTest {
        val repository = FakeRoomRepository(showTimeFailure = true)
        val viewModel = RoomViewModel(repository)
        advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state is RoomUiState.Ready)
        assertNull((state as RoomUiState.Ready).showTime)
    }

    @Test fun refreshUsesLatestRoomRequest() = runTest {
        val repository = FakeRoomRepository()
        val viewModel = RoomViewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(repository.roomLoads >= 1)
    }

    @Test fun staleNonCooperativeResultCannotOverwriteNewRefresh() = runTest {
        val firstRoomGate = CompletableDeferred<Unit>()
        val repository = object : RoomRepository {
            var calls = 0
            override suspend fun loadRoom(roomId: String): RoomInfo {
                calls++
                if (calls == 1) withContext(NonCancellable) { firstRoomGate.await() }
                return RoomInfo(roomId, if (calls == 1) "old" else "new", "anchor", null, null, 1, null, 1, 0)
            }

            override suspend fun loadShowTime(roomId: String): Long? = null
        }
        val viewModel = RoomViewModel(repository)
        runCurrent()
        viewModel.refresh()
        runCurrent()
        firstRoomGate.complete(Unit)
        advanceUntilIdle()
        assertTrue((viewModel.state.value as RoomUiState.Ready).room.title == "new")
    }

    private class FakeRoomRepository(private val showTimeFailure: Boolean = false) : RoomRepository {
        var roomLoads = 0
        override suspend fun loadRoom(roomId: String): RoomInfo {
            roomLoads++
            return RoomInfo(roomId, "title", "anchor", null, null, 1, "intro", 1, 0)
        }

        override suspend fun loadShowTime(roomId: String): Long? {
            if (showTimeFailure) error("h5room unavailable")
            return 1700000000L
        }
    }
}
