package dev.e2b.sdk.e2e;

import dev.e2b.sdk.Sandbox;
import dev.e2b.sdk.model.EntryInfo;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * J07/J08: filesystem read/write, directory ops, binary, download URL.
 */
class FilesystemE2eTest extends E2eTestBase {

    @Test
    void textFilesNestedDirsRenameAndJson() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            sandbox.getFiles().write("/tmp/test_write.txt", "Hello, E2B!");
            assertEquals("Hello, E2B!", sandbox.getFiles().read("/tmp/test_write.txt").trim());

            sandbox.getFiles().write("/tmp/nested/deep/path.txt", "nested content");
            assertEquals("nested content", sandbox.getFiles().read("/tmp/nested/deep/path.txt").trim());

            String json = "{\"sandbox_id\":\"" + sandbox.getSandboxId() + "\"}";
            sandbox.getFiles().write("/tmp/metadata.json", json);
            assertTrue(sandbox.getFiles().read("/tmp/metadata.json").contains(sandbox.getSandboxId()));

            assertTrue(sandbox.getFiles().makeDir("/tmp/new_dir/sub/deep"));
            sandbox.getFiles().write("/tmp/new_dir/sub/deep/file.txt", "deep file");
            sandbox.getFiles().rename("/tmp/new_dir/sub/deep/file.txt", "/tmp/new_dir/sub/deep/moved.txt");
            assertEquals("deep file", sandbox.getFiles().read("/tmp/new_dir/sub/deep/moved.txt").trim());

            assertTrue(sandbox.getFiles().exists("/tmp/new_dir/sub/deep/moved.txt"));
            EntryInfo info = sandbox.getFiles().getInfo("/tmp/new_dir/sub/deep/moved.txt");
            assertNotNull(info.getPath());

            List<EntryInfo> entries = sandbox.getFiles().list("/tmp/new_dir", 2, null);
            assertFalse(entries.isEmpty());

            String downloadUrl = sandbox.downloadUrl("/tmp/test_write.txt");
            assertNotNull(downloadUrl);
            assertTrue(downloadUrl.contains("path=") || downloadUrl.contains("test_write.txt"));

            String fileDownloadUrl = sandbox.getFiles().downloadUrl("/tmp/test_write.txt", null);
            assertEquals(downloadUrl, fileDownloadUrl);
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }

    @Test
    void binaryWriteReadAndRemove() {
        Sandbox sandbox = E2eSupport.createSandbox(config);
        try {
            byte[] payload = new byte[] {0x00, 0x01, 0x02, (byte) 0xff};
            sandbox.getFiles().write("/tmp/binary.bin", payload);
            byte[] readBack = sandbox.getFiles().readBytes("/tmp/binary.bin", null);
            assertArrayEquals(payload, readBack);

            String asString = new String(readBack, StandardCharsets.ISO_8859_1);
            assertEquals(new String(payload, StandardCharsets.ISO_8859_1), asString);

            sandbox.getFiles().remove("/tmp/binary.bin");
            assertFalse(sandbox.getFiles().exists("/tmp/binary.bin"));
        } finally {
            E2eSupport.killQuietly(sandbox);
        }
    }
}
