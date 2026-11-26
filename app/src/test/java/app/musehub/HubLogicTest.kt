package app.musehub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubLogicTest {
    @Test fun taskFlow() {
        assertEquals(listOf("in_progress"), allowedNextStatuses("pending"))
        assertEquals(listOf("review"), allowedNextStatuses("in_progress"))
        assertEquals(listOf("done", "rejected"), allowedNextStatuses("review"))
        assertEquals(listOf("in_progress"), allowedNextStatuses("rejected"))
        assertEquals(emptyList<String>(), allowedNextStatuses("done"))
        assertEquals("待认领", statusLabel("pending"))
        assertEquals("驳回", statusLabel("rejected"))
    }

    @Test fun sensitiveFilenames() {
        assertTrue(isSensitiveFilename("my-password.txt"))
        assertTrue(isSensitiveFilename("id_ed25519"))
        assertTrue(isSensitiveFilename("cert.pem"))
        assertTrue(isSensitiveFilename("a.key"))
        assertFalse(isSensitiveFilename("batch-84-prompt.md"))
        assertFalse(isSensitiveFilename("photo.jpg"))
    }

    @Test fun taskCategoryMapping() {
        assertEquals("待处理", taskCategory("pending"))
        assertEquals("进行中", taskCategory("in_progress"))
        assertEquals("进行中", taskCategory("review"))
        assertEquals("已完成", taskCategory("done"))
        assertEquals("失败", taskCategory("rejected"))
    }

    @Test fun fileCategory() {
        assertEquals("文档", fileCategory("a.pdf"))
        assertEquals("图片", fileCategory("b.jpg"))
        assertEquals("其他", fileCategory("c.zip"))
    }

    @Test fun displayAgentName() {
        val agents = listOf(Agent("muse-2", "二号", "第二个 Muse，执行"), Agent("muse-3", "", "第三个 Muse，执行"))
        assertEquals("二号", displayAgentName(agents, "muse-2"))
        assertEquals("muse-3", displayAgentName(agents, "muse-3"))
        assertEquals("muse-9", displayAgentName(agents, "muse-9"))
    }

    @Test fun quoteSnippet() {
        assertEquals("hello", quoteSnippet("hello"))
        assertEquals("a…", quoteSnippet("abcdef", 1))
        assertEquals("one two", quoteSnippet("one\ntwo"))
        assertEquals("", quoteSnippet(""))
    }

    @Test fun formatSize() {
        assertEquals("512 B", formatSize(512))
        assertEquals("12.0 KB", formatSize(12288))
        assertEquals("8.4 MB", formatSize(8808038))
    }

    @Test fun photoFilename() {
        val name = photoFilename()
        assertTrue(name.startsWith("photo-"))
        assertTrue(name.endsWith(".jpg"))
    }
}
