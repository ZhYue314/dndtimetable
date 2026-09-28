package com.dndtimetable.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 首页按钮方向决策（本 App 静音会话开关）测试。 */
class MainViewModelKtTest {

    @Test
    fun `无会话时点击打开`() {
        assertTrue(toggleShouldStart(sessionActive = false))
    }

    @Test
    fun `有会话时点击关闭`() {
        assertFalse(toggleShouldStart(sessionActive = true))
    }
}
