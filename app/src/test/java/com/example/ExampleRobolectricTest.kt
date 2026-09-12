package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.studio.PodcastClipStudioContent
import com.example.ui.screens.studio.StudioScreen
import com.example.ui.theme.AutoPostStudioTheme
import com.example.viewmodel.AutoPostViewModel
import com.example.viewmodel.StudioSubMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AutoPost Studio", appName)
  }

  @Test
  fun `load podcast for topic executes safely`() = kotlinx.coroutines.test.runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    try {
      val app = ApplicationProvider.getApplicationContext<AutoPostApplication>()
      val viewModel = AutoPostViewModel(app)
      viewModel.loadPodcastForTopic("test topic")
      testScheduler.advanceUntilIdle()
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `send plan item with podcast clip format to studio executes safely`() = kotlinx.coroutines.test.runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    try {
      val app = ApplicationProvider.getApplicationContext<AutoPostApplication>()
      val viewModel = AutoPostViewModel(app)
      val item = com.example.data.local.entity.ContentPlanItemEntity(
        id = 101L,
        planId = 1L,
        dayNumber = 1,
        scheduledDateMillis = System.currentTimeMillis(),
        title = "Strategi Podcast Viral",
        format = com.example.data.local.entity.ContentFormat.PODCAST_CLIP,
        hook = "Bagaimana podcast bisa viral dalam 3 hari?",
        captionDraft = "Simak pembahasan podcast ini...",
        hashtags = "#podcast #viral"
      )
      viewModel.sendPlanItemToStudio(item)
      testScheduler.advanceUntilIdle()
      assertEquals(StudioSubMode.PODCAST_CLIP, viewModel.studioSubMode.value)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @Test
  fun `render studio podcast clip screen`() {
    val app = ApplicationProvider.getApplicationContext<AutoPostApplication>()
    val viewModel = AutoPostViewModel(app)
    viewModel.setStudioSubMode(StudioSubMode.PODCAST_CLIP)

    composeTestRule.setContent {
      AutoPostStudioTheme {
        StudioScreen(viewModel)
      }
    }
  }
}
