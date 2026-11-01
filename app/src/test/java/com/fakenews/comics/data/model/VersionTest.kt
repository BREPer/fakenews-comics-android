package com.fakenews.comics.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VersionTest {
    @Test
    fun `同主版本不提示`() {
        assertEquals(2, majorOf("2.0.2"))
        assertNull(versionWarning("2.0.2"))
        assertNull(versionWarning("2.9.0"))
    }

    @Test
    fun `主版本不一致给出警告`() {
        assertNotNull(versionWarning("3.0.0"))
        assertNotNull(versionWarning("1.9.9"))
    }

    @Test
    fun `无法解析的版本不误报`() {
        assertNull(majorOf(null))
        assertNull(majorOf("dev"))
        assertNull(versionWarning(null))
    }
}
