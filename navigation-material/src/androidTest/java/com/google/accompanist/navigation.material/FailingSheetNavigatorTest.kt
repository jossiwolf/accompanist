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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.Navigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterialNavigationApi::class)
class FailingSheetNavigatorTest {

    @get:Rule
    val rule = createComposeRule()

    @Composable
    fun rememberTestNavHostController(vararg navigators: Navigator<*>): TestNavHostController {
        val context = LocalContext.current
        return remember(context, navigators) {
            TestNavHostController(context).also { controller ->
                navigators.forEach { navigator ->
                    controller.navigatorProvider.addNavigator(
                        navigator
                    )
                }
            }
        }
    }

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
        height = 700
        rule.mainClock.advanceTimeBy(animationDuration - 100L)
        rule.waitForIdle()
        assertThat(navigator.navigatorSheetState.isVisible).isTrue()

        rule.runOnUiThread { navController.popBackStack() }
        rule.mainClock.advanceTimeBy(animationDuration.toLong())
        rule.waitForIdle()
        assertThat(navigator.navigatorSheetState.isVisible).isFalse()
    }
}