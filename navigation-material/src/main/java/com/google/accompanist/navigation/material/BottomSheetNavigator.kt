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

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.SwipeProgress
import androidx.compose.material.SwipeableDefaults
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.lifecycle.Lifecycle
import androidx.navigation.FloatingWindow
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.NavigatorState
import com.google.accompanist.navigation.material.BottomSheetNavigator.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform

/**
 * The state of a [ModalBottomSheetLayout] that the [BottomSheetNavigator] drives
 *
 * @param sheetState The sheet state that is driven by the [BottomSheetNavigator]
 */
@ExperimentalMaterialNavigationApi
@OptIn(ExperimentalMaterialApi::class)
@Stable
class BottomSheetNavigatorSheetState(internal val sheetState: ModalBottomSheetState) {
    /**
     * @see ModalBottomSheetState.isVisible
     */
    val isVisible: Boolean
        get() = sheetState.isVisible

    /**
     * @see ModalBottomSheetState.currentValue
     */
    val currentValue: ModalBottomSheetValue
        get() = sheetState.currentValue

    /**
     * @see ModalBottomSheetState.targetValue
     */
    val targetValue: ModalBottomSheetValue
        get() = sheetState.targetValue

    /**
     * @see ModalBottomSheetState.offset
     */
    @Deprecated(
        message = "BottomSheetNavigatorSheetState#offset has been removed",
        level = DeprecationLevel.ERROR
    )
    val offset: State<Float>
        get() = error("BottomSheetNavigatorSheetState#offset has been removed")

    /**
     * @see ModalBottomSheetState.direction
     */
    @Deprecated(
        message = "BottomSheetNavigatorSheetState#direction has been removed",
        level = DeprecationLevel.ERROR
    )
    val direction: Float
        get() = error("BottomSheetNavigatorSheetState#direction has been removed.")

    /**
     * @see ModalBottomSheetState.progress
     */
    @Deprecated(
        message = "BottomSheetNavigatorSheetState#progress has been removed",
        level = DeprecationLevel.ERROR
    )
    val progress: SwipeProgress<ModalBottomSheetValue>
        get() = error("BottomSheetNavigatorSheetState#progress has been removed")
}

/**
 * Create and remember a [BottomSheetNavigator]
 */
@ExperimentalMaterialNavigationApi
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun rememberBottomSheetNavigator(
    animationSpec: AnimationSpec<Float> = SwipeableDefaults.AnimationSpec
): BottomSheetNavigator {
    val initialSheetState = rememberModalBottomSheetState(
        ModalBottomSheetValue.Hidden,
        skipHalfExpanded = false,
        animationSpec = animationSpec
    )
    val navigator = remember { BottomSheetNavigator(skipHalfExpandedInitially = false, initialSheetState)}
    DisposableEffect(navigator.skipHalfExpanded) {
        navigator.sheetState = ModalBottomSheetState(
            initialValue = ModalBottomSheetValue.Hidden,
            animationSpec = animationSpec,
            isSkipHalfExpanded = navigator.skipHalfExpanded
        )
        onDispose {  }
    }
    return navigator
}

/**
 * Navigator that drives a [ModalBottomSheetState] for use of [ModalBottomSheetLayout]s
 * with the navigation library. Every destination using this Navigator must set a valid
 * [Composable] by setting it directly on an instantiated [Destination] or calling
 * [androidx.navigation.compose.material.bottomSheet].
 *
 * <b>The [sheetContent] [Composable] will always host the latest entry of the back stack. When
 * navigating from a [BottomSheetNavigator.Destination] to another
 * [BottomSheetNavigator.Destination], the content of the sheet will be replaced instead of a
 * new bottom sheet being shown.</b>
 *
 * When the sheet is dismissed by the user, the [state]'s [NavigatorState.backStack] will be popped.
 *
 * @param sheetState The [ModalBottomSheetState] that the [BottomSheetNavigator] will use to
 * drive the sheet state
 */
@ExperimentalMaterialNavigationApi
@OptIn(ExperimentalMaterialApi::class)
@Navigator.Name("BottomSheetNavigator")
class BottomSheetNavigator(
    skipHalfExpandedInitially: Boolean,
    initialSheetState: ModalBottomSheetState
) : Navigator<Destination>() {

    internal var sheetState by mutableStateOf(initialSheetState)

    private var attached by mutableStateOf(false)

    /**
     * Get the back stack from the [state]. In some cases, the [sheetContent] might be composed
     * before the Navigator is attached, so we specifically return an empty flow if we aren't
     * attached yet.
     */
    private val backStack: StateFlow<List<NavBackStackEntry>>
        get() = if (attached) {
            state.backStack
        } else {
            MutableStateFlow(emptyList())
        }

    /**
     * Get the transitionsInProgress from the [state]. In some cases, the [sheetContent] might be
     * composed before the Navigator is attached, so we specifically return an empty flow if we
     * aren't attached yet.
     */
    private val transitionsInProgress: StateFlow<Set<NavBackStackEntry>>
        get() = if (attached) {
            state.transitionsInProgress
        } else {
            MutableStateFlow(emptySet())
        }

    /**
     * Access properties of the [ModalBottomSheetLayout]'s [ModalBottomSheetState]
     */
    val navigatorSheetState = BottomSheetNavigatorSheetState(sheetState)

    var skipHalfExpanded by mutableStateOf(skipHalfExpandedInitially)
        private set

    /**
     * A [Composable] function that hosts the current sheet content. This should be set as
     * sheetContent of your [ModalBottomSheetLayout].
     */
    val sheetContent: @Composable ColumnScope.() -> Unit =
        movableContentWithReceiverOf<ColumnScope> {
            val saveableStateHolder = rememberSaveableStateHolder()
            val transitionsInProgressEntries by transitionsInProgress.collectAsState()

            val retainedEntry by produceState<NavBackStackEntry?>(initialValue = null) {
                backStack
                    .map { backStackEntries ->
                        backStackEntries.lastOrNull { entry ->
                            // We might have entries in the back stack that aren't started
                            // currently, so filter these
                            entry.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)
                        }
                    }
                    .transform { latestEntry ->
                        sheetState.hide()
                        // Update destination-level flags if navigating to a sheet destination
                        if (latestEntry != null) {
                            val skipHalfExpandedAtDestination =
                                (latestEntry.destination as Destination).skipHalfExpanded
                            val sheetStateRequiresUpdate = skipHalfExpanded != skipHalfExpandedAtDestination
                            if (sheetStateRequiresUpdate) {
                                // We will update the sheet state from an effect, so we depend on
                                // recomposition to happen
                                val sheetStateBeforeUpdate = sheetState
                                // 1. Update the skipHalfExpanded flag to trigger recomposition
                                skipHalfExpanded = skipHalfExpandedAtDestination
                                var sheetStateUpdated = false
                                // Wait until the sheet state has been updated
                                while(!sheetStateUpdated) {
                                    withFrameMillis {
                                        sheetStateUpdated = sheetState != sheetStateBeforeUpdate
                                    }
                                }
                            }
                        }
                        emit(latestEntry)
                    }
                    .collectLatest { value = it }
            }

            // Mark all of the entries' transitions as complete, except for the entry we are
            // currently displaying because it will have its transition completed when the sheet's
            // animation has completed
            DisposableEffect(transitionsInProgressEntries) {
                transitionsInProgressEntries.forEach {
                    if (it != retainedEntry) state.markTransitionComplete(it)
                }
                onDispose { }
            }

            if (retainedEntry != null) {
                LaunchedEffect(retainedEntry) {
                    sheetState.show()
                }
            }

            SheetContentHost(
                backStackEntry = retainedEntry,
                sheetState = sheetState,
                saveableStateHolder = saveableStateHolder,
                onSheetShown = { backStackEntry ->
                    state.markTransitionComplete(backStackEntry)
                },
                onSheetDismissed = { backStackEntry ->
                    // Sheet dismissal can be started through popBackStack in which case we have a
                    // transition that we'll want to complete
                    if (transitionsInProgressEntries.contains(backStackEntry)) {
                        state.markTransitionComplete(backStackEntry)
                    } else {
                        state.pop(popUpTo = backStackEntry, saveState = false)
                    }
                }
            )
        }

    override fun onAttach(state: NavigatorState) {
        super.onAttach(state)
        attached = true
    }

    override fun createDestination(): Destination = Destination(
        navigator = this,
        content = {}
    )

    override fun navigate(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Extras?
    ) {
        entries.forEach { entry ->
            state.pushWithTransition(entry)
        }
    }

    override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
        state.popWithTransition(popUpTo, savedState)
    }

    /**
     * [NavDestination] specific to [BottomSheetNavigator]
     */
    @NavDestination.ClassType(Composable::class)
    class Destination(
        navigator: BottomSheetNavigator,
        internal val skipHalfExpanded: Boolean = false,
        internal val content: @Composable ColumnScope.(NavBackStackEntry) -> Unit
    ) : NavDestination(navigator), FloatingWindow
}
