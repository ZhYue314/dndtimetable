package com.dndtimetable.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun versionCompare() {
        assertTrue(UpdateChecker.isNewer("0.3.0", "0.2.0"))
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.9.0"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.9.9"))
        assertTrue(UpdateChecker.isNewer("0.2.1", "0.2"))
        assertFalse(UpdateChecker.isNewer("0.2.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewer("0.1.9", "0.2.0"))
        assertFalse(UpdateChecker.isNewer("0.2", "0.2.0"))   // 段缺失按 0，不算新
    }

    @Test
    fun locationTag() {
        // 源 1（302 Location）→ tag 提取：绝对/相对地址都要能解出 tag
        assertEquals("v1.0", UpdateChecker.tagFromLocation(
            "https://github.com/ZhYue314/dndtimetable/releases/download/v1.0/app-release.apk"))
        assertEquals("v1.0", UpdateChecker.tagFromLocation(
            "/ZhYue314/dndtimetable/releases/download/v1.0/app-release.apk"))
        assertNull(UpdateChecker.tagFromLocation(null))
        assertNull(UpdateChecker.tagFromLocation("https://example.com/foo/bar"))
        assertNull(UpdateChecker.tagFromLocation("https://github.com/x/y/releases/download//a.apk"))
    }
}
