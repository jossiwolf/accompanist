/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.accompanist.navigation.material

import android.os.Bundle
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.testing.TestNavigatorState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToLong

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialNavigationApi::class)
internal class BottomSheetNavigatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testNavigateAddsDestinationToBackStack(): Unit = runBlocking {
        val sheetState = ModalBottomSheetState(ModalBottomSheetValue.Hidden)
        val navigatorState = TestNavigatorState()
        val navigator = BottomSheetNavigator(sheetState)

        navigator.onAttach(navigatorState)
        val entry = navigatorState.createBackStackEntry(navigator.createDestination(), null)
        navigator.navigate(listOf(entry), null, null)

        assertWithMessage("The back stack entry has been added to the back stack")
            .that(navigatorState.backStack.value)
            .containsExactly(entry)
    }

    @Test
    fun testNavigateAddsDestinationToBackStackAndKeepsPrevious(): Unit = runBlocking {
        val sheetState = ModalBottomSheetState(ModalBottomSheetValue.Hidden)
        val navigator = BottomSheetNavigator(sheetState)
        val navigatorState = TestNavigatorState()

        navigator.onAttach(navigatorState)
        val firstEntry = navigatorState.createBackStackEntry(navigator.createDestination(), null)
        val secondEntry = navigatorState.createBackStackEntry(navigator.createDestination(), null)

        navigator.navigate(listOf(firstEntry), null, null)
        assertWithMessage("The first entry has been added to the back stack")
            .that(navigatorState.backStack.value)
            .containsExactly(firstEntry)

        navigator.navigate(listOf(secondEntry), null, null)
        assertWithMessage(
            "The second entry has been added to the back stack and it still " +
                "contains the first entry"
        )
            .that(navigatorState.backStack.value)
            .containsExactly(firstEntry, secondEntry)
            .inOrder()
    }

    @Test
    fun testNavigateComposesDestinationAndDisposesPreviousDestination(): Unit = runBlocking {
        val sheetState = ModalBottomSheetState(ModalBottomSheetValue.Hidden)
        val navigator = BottomSheetNavigator(sheetState)
        val navigatorState = TestNavigatorState()
        navigator.onAttach(navigatorState)

        composeTestRule.setContent {
            Column { navigator.sheetContent(this) }
        }

        var firstDestinationCompositions = 0
        val firstDestinationContentTag = "firstSheetContentTest"
        val firstDestination = BottomSheetNavigator.Destination(navigator) {
            DisposableEffect(Unit) {
                firstDestinationCompositions++
                onDispose { firstDestinationCompositions = 0 }
            }
            Text("Fake Sheet Content", Modifier.testTag(firstDestinationContentTag))
        }
        val firstEntry = navigatorState.createBackStackEntry(firstDestination, null)

        var secondDestinationCompositions = 0
        val secondDestinationContentTag = "secondSheetContentTest"
        val secondDestination = BottomSheetNavigator.Destination(navigator) {
            DisposableEffect(Unit) {
                secondDestinationCompositions++
                onDispose { secondDestinationCompositions = 0 }
            }
            Box(
                Modifier
                    .size(64.dp)
                    .testTag(secondDestinationContentTag))
        }
        val secondEntry = navigatorState.createBackStackEntry(secondDestination, null)

        navigator.navigate(listOf(firstEntry), null, null)
        composeTestRule.awaitIdle()

        composeTestRule.onNodeWithTag(firstDestinationContentTag).assertExists()
        composeTestRule.onNodeWithTag(secondDestinationContentTag).assertDoesNotExist()
        assertWithMessage("First destination should have been composed exactly once")
            .that(firstDestinationCompositions).isEqualTo(1)
        assertWithMessage("Second destination should not have been composed yet")
            .that(secondDestinationCompositions).isEqualTo(0)

        navigator.navigate(listOf(secondEntry), null, null)
        composeTestRule.awaitIdle()

        composeTestRule.onNodeWithTag(firstDestinationContentTag).assertDoesNotExist()
        composeTestRule.onNodeWithTag(secondDestinationContentTag).assertExists()
        assertWithMessage("First destination has not been disposed")
            .that(firstDestinationCompositions).isEqualTo(0)
        assertWithMessage("Second destination should have been composed exactly once")
            .that(secondDestinationCompositions).isEqualTo(1)
    }

    @Test
    fun testBackStackEntryPoppedAfterManualSheetDismiss(): Unit = runBlocking {
        val navigatorState = TestNavigatorState()
        val sheetState = ModalBottomSheetState(ModalBottomSheetValue.Hidden)
        val navigator = BottomSheetNavigator(sheetState = sheetState)
        navigator.onAttach(navigatorState)

        val bodyContentTag = "testBodyContent"

        composeTestRule.setContent {
            ModalBottomSheetLayout(
                bottomSheetNavigator = navigator,
                content = { Box(
                    Modifier
                        .fillMaxSize()
                        .testTag(bodyContentTag)) }
            )
        }

        val destination = navigator.createFakeDestination()
        val backStackEntry = navigatorState.createBackStackEntry(destination, null)
        navigator.navigate(listOf(backStackEntry), null, null)
        composeTestRule.awaitIdle()

        assertWithMessage("Navigated to destination")
            .that(navigatorState.backStack.value)
            .containsExactly(backStackEntry)
        assertWithMessage("Bottom sheet shown")
            .that(sheetState.isVisible).isTrue()

        composeTestRule.onNodeWithTag(bodyContentTag).performClick()
        composeTestRule.awaitIdle()
        assertWithMessage("Sheet should be hidden")
            .that(sheetState.isVisible).isFalse()
        assertWithMessage("Back stack entry should be popped off the back stack")
            .that(navigatorState.backStack.value)
            .isEmpty()
    }

    @Test
    fun testSheetShownAfterNavControllerRestoresState() = runBlocking {
        lateinit var navController: NavHostController
        lateinit var navigator: BottomSheetNavigator
        var savedState: Bundle? = null
        var compositionState by mutableStateOf(0)

        val sheetState = ModalBottomSheetState(ModalBottomSheetValue.Hidden)

        composeTestRule.setContent {
            navigator = remember { BottomSheetNavigator(sheetState) }
            navController = rememberNavController(navigator)
            if (savedState != null) navController.restoreState(savedState)
            if (compositionState == 0) {
                ModalBottomSheetLayout(
                    bottomSheetNavigator = navigator
                ) {
                    NavHost(navController, startDestination = "first") {
                        bottomSheet("first") {
                            Text("Hello!")
                        }
                    }
                }
            }
        }

        savedState = navController.saveState()

        // Dispose the ModalBottomSheetLayout
        compositionState = 1
        composeTestRule.awaitIdle()

        assertWithMessage("Bottom Sheet is visible")
            .that(sheetState.isVisible).isFalse()

        // Recompose with the ModalBottomSheetLayout
        compositionState = 0
        composeTestRule.awaitIdle()

        assertWithMessage("Destination is first destination")
            .that(navController.currentDestination?.route)
            .isEqualTo("first")
        assertWithMessage("Bottom sheet is visible")
            .that(sheetState.isVisible).isTrue()
    }

    @Test
    fun testNavigateCompletesEntriesTransitions() = runBlocking {
        val sheetState = ModalBottomSheetState(ModalBottomSheetValue.Hidden)
        val navigator = BottomSheetNavigator(sheetState)
        val navigatorState = TestNavigatorState()

        navigator.onAttach(navigatorState)

        composeTestRule.setContent {
            ModalBottomSheetLayout(
                bottomSheetNavigator = navigator,
                content = { Box(Modifier.fillMaxSize()) }
            )
        }

        val backStackEntry1 = navigatorState.createBackStackEntry(
            navigator.createFakeDestination(), null
        )
        val backStackEntry2 = navigatorState.createBackStackEntry(
            navigator.createFakeDestination(), null
        )

        navigator.navigate(
            entries = listOf(backStackEntry1, backStackEntry2),
            navOptions = null,
            navigatorExtras = null
        )

        composeTestRule.awaitIdle()

        assertThat(navigatorState.transitionsInProgress.value)
            .doesNotContain(backStackEntry1)

        assertThat(navigatorState.transitionsInProgress.value)
            .doesNotContain(backStackEntry2)
    }

    @Test
    fun testComposeSheetContentBeforeNavigatorAttached(): Unit = runBlocking {
        val sheetState = ModalBottomSheetState(ModalBottomSheetValue.Hidden)
        val navigator = BottomSheetNavigator(sheetState)
        val navigatorState = TestNavigatorState()

        composeTestRule.setContent {
            ModalBottomSheetLayout(
                bottomSheetNavigator = navigator,
                content = { Box(Modifier.fillMaxSize()) }
            )
        }

        // Attach the state only after accessing the navigator's sheetContent in
        // ModalBottomSheetLayout
        navigator.onAttach(navigatorState)

        val entry = navigatorState.createBackStackEntry(
            navigator.createFakeDestination(), null
        )

        navigator.navigate(
            entries = listOf(entry),
            navOptions = null,
            navigatorExtras = null
        )

        composeTestRule.awaitIdle()

        assertWithMessage("The back stack entry has been added to the back stack")
            .that(navigatorState.backStack.value)
            .containsExactly(entry)
    }

    @Test
    fun testBackPressedDestroysEntry() {
        lateinit var onBackPressedDispatcher: OnBackPressedDispatcher
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            val bottomSheetNavigator = rememberBottomSheetNavigator()
            navController = rememberNavController(bottomSheetNavigator)
            onBackPressedDispatcher =
                LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher!!

            ModalBottomSheetLayout(bottomSheetNavigator) {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "mainScreen"
                    ) {

                        composable(
                            route = "mainScreen",
                            content = {
                                Button(onClick = { navController.navigate("bottomSheet") }) {
                                    Text(text = "open drawer")
                                }
                            }
                        )

                        bottomSheet(
                            route = "bottomSheet",
                            content = {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = "bottomSheet"
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("open drawer").performClick()

        lateinit var bottomSheetEntry: NavBackStackEntry

        composeTestRule.runOnIdle {
            bottomSheetEntry = navController.currentBackStackEntry!!
            onBackPressedDispatcher.onBackPressed()
        }

        composeTestRule.runOnIdle {
            assertThat(bottomSheetEntry.lifecycle.currentState).isEqualTo(Lifecycle.State.DESTROYED)
        }
    }

    @Test
    fun testChangingSheetContent() {
        val animationDuration = 2000
        val animationSpec = tween<Float>(animationDuration)
        lateinit var navigator: BottomSheetNavigator
        lateinit var navController: NavHostController
        var height: Dp by mutableStateOf(20.dp)

        composeTestRule.setContent {
            navigator = rememberBottomSheetNavigator(animationSpec)
            navController = rememberNavController(navigator)
            navController.currentBackStackEntryAsState().value
            ModalBottomSheetLayout(navigator) {
                NavHost(navController, "first") {
                    composable("first") {
                        Box(Modifier.fillMaxSize())
                    }
                    bottomSheet("sheet") {
                        Box(Modifier.height(height))
                    }
                }
            }
        }

        composeTestRule.mainClock.autoAdvance = true

        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread {
            navController.navigate("sheet")
        }
        composeTestRule.mainClock.advanceTimeBy(100)
        height = 700.dp
        composeTestRule.mainClock.advanceTimeBy(animationDuration - 100L)
        composeTestRule.waitForIdle()
        assertThat(navigator.navigatorSheetState.isVisible).isTrue()

        composeTestRule.runOnUiThread { navController.navigate("first") }
        composeTestRule.mainClock.advanceTimeBy(animationDuration.toLong())
        composeTestRule.waitForIdle()
        assertThat(navigator.navigatorSheetState.isVisible).isFalse()
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test
    fun testPopBackStackHidesSheetWithAnimation() {
        val animationDuration = 2000
        val animationSpec = tween<Float>(animationDuration)
        lateinit var navigator: BottomSheetNavigator
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            navigator = rememberBottomSheetNavigator(animationSpec)
            navController = rememberNavController(navigator)
            navController.currentBackStackEntryAsState().value
            ModalBottomSheetLayout(navigator) {
                NavHost(navController, "first") {
                    composable("first") {
                        Box(Modifier.fillMaxSize())
                    }
                    bottomSheet("sheet") {
                        Box(Modifier.height(200.dp))
                    }
                }
            }
        }

        composeTestRule.runOnUiThread { navController.navigate("sheet") }
        composeTestRule.waitForIdle()
        assertThat(navigator.navigatorSheetState.isVisible).isTrue()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread { navController.popBackStack() }

        val firstAnimationTimeBreakpoint = (animationDuration * 0.9).roundToLong()

        composeTestRule.mainClock.advanceTimeBy(firstAnimationTimeBreakpoint)
        assertThat(navigator.navigatorSheetState.currentValue)
            .isAnyOf(ModalBottomSheetValue.HalfExpanded, ModalBottomSheetValue.Expanded)
        assertThat(navigator.navigatorSheetState.targetValue)
            .isEqualTo(ModalBottomSheetValue.Hidden)

        composeTestRule.runOnUiThread { navController.navigate("first") }

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        assertThat(navigator.navigatorSheetState.currentValue)
            .isEqualTo(ModalBottomSheetValue.Hidden)
    }

    @Test
    fun testTapOnScrimDismissesSheetAndPopsBackStack() {
        val animationDuration = 2000
        val animationSpec = tween<Float>(animationDuration)
        lateinit var navigator: BottomSheetNavigator
        lateinit var navController: NavHostController
        val sheetLayoutTestTag = "sheetLayout"
        val homeDestination = "home"
        val sheetDestination = "sheet"

        composeTestRule.setContent {
            navigator = rememberBottomSheetNavigator(animationSpec)
            navController = rememberNavController(navigator)
            ModalBottomSheetLayout(navigator, Modifier.testTag(sheetLayoutTestTag)) {
                NavHost(navController, homeDestination) {
                    composable(homeDestination) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Red))
                    }
                    bottomSheet(sheetDestination) {
                        Box(
                            Modifier
                                .height(200.dp)
                                .fillMaxWidth()
                                .background(Color.Green)) {
                            Text("Hello!")
                        }
                    }
                }
            }
        }

        assertThat(navController.currentBackStackEntry?.destination?.route).isEqualTo(
            homeDestination
        )
        assertThat(navigator.navigatorSheetState.isVisible).isFalse()

        composeTestRule.runOnUiThread { navController.navigate(sheetDestination) }
        composeTestRule.waitForIdle()

        assertThat(navController.currentBackStackEntry?.destination?.route).isEqualTo(
            sheetDestination
        )
        assertThat(navigator.navigatorSheetState.isVisible).isTrue()

        composeTestRule.onNodeWithTag(sheetLayoutTestTag)
            .performTouchInput { click(position = topCenter) }

        composeTestRule.waitForIdle()
        assertThat(navigator.navigatorSheetState.isVisible).isFalse()
    }

    /**
     * Create a [BottomSheetNavigator.Destination] with some fake content
     * Having an empty Composable will result in the sheet's height being 0 which crashes
     * ModalBottomSheetLayout
     */
    private fun BottomSheetNavigator.createFakeDestination() =
        BottomSheetNavigator.Destination(this) {
            Text("Fake Sheet Content")
        }
}
