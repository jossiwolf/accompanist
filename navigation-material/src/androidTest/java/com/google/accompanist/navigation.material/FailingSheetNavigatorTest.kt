/*
 * Copyright 2022 The Android Open Source Project
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

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToLong

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterialNavigationApi::class)
class FailingSheetNavigatorTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun testChangingSheetContent() {
        val animationDuration = 2000
        val animationSpec = tween<Float>(animationDuration)
        lateinit var navigator: BottomSheetNavigator
        lateinit var navController: NavHostController
        var height: Dp by mutableStateOf(20.dp)

        rule.setContent {
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

        rule.mainClock.autoAdvance = true

        rule.waitForIdle()
        rule.runOnUiThread {
            navController.navigate("sheet")
        }
        rule.mainClock.advanceTimeBy(100)
        height = 700.dp
        rule.mainClock.advanceTimeBy(animationDuration - 100L)
        rule.waitForIdle()
        assertThat(navigator.navigatorSheetState.isVisible).isTrue()

        runBlocking { delay(5000) }

        rule.runOnUiThread { navController.navigate("first") }
        rule.mainClock.advanceTimeBy(animationDuration.toLong())
        rule.waitForIdle()
        assertThat(navigator.navigatorSheetState.isVisible).isFalse()
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test
    fun testPopBackStackHidesSheetWithAnimation() {
        val animationDuration = 2000
        val animationSpec = tween<Float>(animationDuration)
        lateinit var navigator: BottomSheetNavigator
        lateinit var navController: NavHostController

        rule.setContent {
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

        rule.runOnUiThread { navController.navigate("sheet") }
        rule.waitForIdle()
        assertThat(navigator.navigatorSheetState.isVisible).isTrue()

        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { navController.popBackStack() }

        val firstAnimationTimeBreakpoint = (animationDuration * 0.9).roundToLong()

        rule.mainClock.advanceTimeBy(firstAnimationTimeBreakpoint)
        assertThat(navigator.navigatorSheetState.currentValue)
            .isAnyOf(ModalBottomSheetValue.HalfExpanded, ModalBottomSheetValue.Expanded)
        assertThat(navigator.navigatorSheetState.targetValue)
            .isEqualTo(ModalBottomSheetValue.Hidden)

        rule.runOnUiThread { navController.navigate("first") }

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
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

        rule.setContent {
            navigator = rememberBottomSheetNavigator(animationSpec)
            navController = rememberNavController(navigator)
            navController.currentBackStackEntryAsState().value
            ModalBottomSheetLayout(navigator, Modifier.testTag(sheetLayoutTestTag)) {
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

        rule.onNodeWithTag(sheetLayoutTestTag)
            .performTouchInput { click(position = topCenter) }

        rule.waitForIdle()
        assertThat(navigator.navigatorSheetState.isVisible).isFalse()
    }
}