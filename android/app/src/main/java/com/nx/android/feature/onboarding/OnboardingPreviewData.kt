package com.nx.android.feature.onboarding

import com.nx.android.appstate.OnboardingState
import com.nx.android.domain.ObFacts
import com.nx.android.domain.ObStep

/**
 * 预览用的假状态：引导的每一步，各有几个样子。
 *
 * 这里没有 @Preview —— 预览在各自的组件文件里（IDE 的惯例：预览就写在它预览的那个组件旁边），
 * 这个文件只放它们共用的数据。
 */

/**
 * 一个假的状态：[step] 是走到哪一步，其余是这一步看到的东西。
 *
 * 事实（[OnboardingUiState.facts]）与这一步看到的字段是同一件事的两面，所以这里一起给。
 */
internal fun previewState(
    step: ObStep,
    storageOk: Boolean = true,
    freeGB: Long = 42,
    envReady: Boolean = false,
    unpacking: Boolean = false,
    unpackStep: String = "",
    unpackError: String = "",
    keysPath: String = "",
    keysOk: Boolean = false,
    keysChecking: Boolean = false,
    keysDetail: String = "",
    keysSkipped: Boolean = false,
): OnboardingUiState = OnboardingUiState(
    active = true,
    step = step,
    facts = ObFacts(
        storageOk = storageOk,
        freeGB = freeGB,
        neededGB = OnboardingState.NEEDED_GB,
        envReady = envReady,
        keysPath = keysPath,
        keysOk = keysOk,
        keysSkipped = keysSkipped,
    ),
    envReady = envReady,
    unpacking = unpacking,
    unpackStep = unpackStep,
    unpackError = unpackError,
    keysOk = keysOk,
    keysChecking = keysChecking,
    keysPath = keysPath,
    keysDetail = keysDetail,
    keysSkipped = keysSkipped,
)

/** 预览里的动作：点了什么都不做 */
internal fun previewActions() = OnboardingActions(
    onNext = {},
    onBack = {},
    onOpenLibrary = {},
    onAskStorage = {},
    onUnpack = {},
    onChooseKeysFile = {},
    onKeysSkippedChange = {},
)
