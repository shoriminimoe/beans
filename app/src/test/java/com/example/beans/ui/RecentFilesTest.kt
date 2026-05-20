package com.example.beans.ui

import com.example.beans.frecency.FakeSharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecentFilesTest {

    private lateinit var prefs: FakeSharedPreferences

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
    }

    @Test
    fun `empty prefs returns empty list`() {
        assertTrue(RecentFiles.getRecent(prefs).isEmpty())
    }

    @Test
    fun `add and retrieve recent file`() {
        RecentFiles.addRecent(prefs, "content://file1")
        val recent = RecentFiles.getRecent(prefs)
        assertEquals(1, recent.size)
        assertEquals("content://file1", recent[0])
    }

    @Test
    fun `most recent file is first`() {
        RecentFiles.addRecent(prefs, "content://file1")
        RecentFiles.addRecent(prefs, "content://file2")
        val recent = RecentFiles.getRecent(prefs)
        assertEquals("content://file2", recent[0])
        assertEquals("content://file1", recent[1])
    }

    @Test
    fun `duplicate is moved to front`() {
        RecentFiles.addRecent(prefs, "content://file1")
        RecentFiles.addRecent(prefs, "content://file2")
        RecentFiles.addRecent(prefs, "content://file1")
        val recent = RecentFiles.getRecent(prefs)
        assertEquals(2, recent.size)
        assertEquals("content://file1", recent[0])
    }

    @Test
    fun `max 10 recent files`() {
        for (i in 1..15) {
            RecentFiles.addRecent(prefs, "content://file$i")
        }
        val recent = RecentFiles.getRecent(prefs)
        assertEquals(10, recent.size)
        assertEquals("content://file15", recent[0])
    }

    @Test
    fun `remove recent file`() {
        RecentFiles.addRecent(prefs, "content://file1")
        RecentFiles.addRecent(prefs, "content://file2")
        RecentFiles.removeRecent(prefs, "content://file1")
        val recent = RecentFiles.getRecent(prefs)
        assertEquals(1, recent.size)
        assertEquals("content://file2", recent[0])
    }

    @Test
    fun `remove nonexistent file is no-op`() {
        RecentFiles.addRecent(prefs, "content://file1")
        RecentFiles.removeRecent(prefs, "content://nonexistent")
        assertEquals(1, RecentFiles.getRecent(prefs).size)
    }
}
