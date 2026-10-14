package dev.e2b.sdk.codeinterpreter;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class Utf8FrameReadersTest {
    private InputStream input(byte[] bytes, int chunk) {
        return new ByteArrayInputStream(bytes) {
            @Override public synchronized int read(byte[] target, int offset, int length) {
                return super.read(target, offset, Math.min(length, chunk));
            }
        };
    }

    private String consume(Reader reader, int chunk) throws IOException {
        StringBuilder result = new StringBuilder();
        char[] chars = new char[chunk + 4];
        int count;
        while ((count = reader.read(chars, 2, chunk)) != -1) {
            assertTrue(count > 0);
            assertEquals(0, chars[0]);
            assertEquals(0, chars[chars.length - 1]);
            result.append(chars, 2, count);
        }
        assertEquals(-1, reader.read(chars, 2, chunk));
        return result.toString();
    }

    @Test void malformedAndSplitUtf8MatchesJdkReaderIncludingSingleCharacterReads() throws Exception {
        Utf8FrameReaders pool = new Utf8FrameReaders();
        Random random = new Random(9102026);
        for (int sample = 0; sample < 120; sample++) {
            byte[] data;
            if (sample == 0) data = "\ufeff一😀x\udbff\udfff".getBytes(StandardCharsets.UTF_8);
            else { data = new byte[sample % 73]; random.nextBytes(data); }
            for (int inChunk : new int[]{1, 2, 3, 8192}) {
                for (int outChunk : new int[]{1, 2, 7, 8192}) {
                    try (Reader expected = new InputStreamReader(input(data, inChunk), StandardCharsets.UTF_8);
                         Reader actual = pool.open(input(data, inChunk))) {
                        assertEquals(consume(expected, outChunk), consume(actual, outChunk),
                                "sample=" + sample + " inputChunk=" + inChunk + " outputChunk=" + outChunk);
                    }
                }
            }
        }
    }

    @Test void fullBuffersAndPartialUtf8AtBufferBoundaryRemainEquivalent() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 8191; i++) text.append('a');
        text.append("😀一");
        for (int i = 0; i < 8192; i++) text.append('b');
        byte[] data = text.toString().getBytes(StandardCharsets.UTF_8);
        try (Reader expected = new InputStreamReader(input(data, 8192), StandardCharsets.UTF_8);
             Reader actual = new Utf8FrameReaders().open(input(data, 8192))) {
            assertEquals(consume(expected, 8192), consume(actual, 8192));
        }
    }

    @Test void unclosedReaderKeepsItsOwnBytesWhenLaterFramesOpen() throws Exception {
        Utf8FrameReaders pool = new Utf8FrameReaders();
        Reader first = pool.open(new ByteArrayInputStream("😀old tail".getBytes(StandardCharsets.UTF_8)));
        assertEquals('\ud83d', first.read());
        Reader second = pool.open(new ByteArrayInputStream("second".getBytes(StandardCharsets.UTF_8)));
        assertTrue(second instanceof InputStreamReader);
        assertEquals("second", consume(second, 3));
        assertEquals("\ude00old tail", consume(first, 1));
        first.close(); second.close();
        Reader third = pool.open(new ByteArrayInputStream("third".getBytes(StandardCharsets.UTF_8)));
        assertEquals("third", consume(third, 7));
        assertThrows(IOException.class, first::read);
        assertThrows(IOException.class, first::ready);
        first.close(); // Must not close the active third frame.
        third.close();
    }

    @Test void readyCloseAndArgumentContractsFollowJdkReader() throws Exception {
        Utf8FrameReaders pool = new Utf8FrameReaders();
        assertThrows(NullPointerException.class, () -> pool.open(null));
        Reader reader = pool.open(new ByteArrayInputStream("😀a".getBytes(StandardCharsets.UTF_8)));
        assertTrue(reader.ready());
        assertThrows(NullPointerException.class, () -> reader.read(null, 0, 1));
        char[] data = new char[2];
        assertThrows(IndexOutOfBoundsException.class, () -> reader.read(data, -1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.read(data, 0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> reader.read(data, 2, 1));
        assertEquals(0, reader.read(data, 0, 0));
        assertEquals('\ud83d', reader.read()); assertTrue(reader.ready());
        assertEquals('\ude00', reader.read()); assertTrue(reader.ready());
        assertEquals('a', reader.read()); assertFalse(reader.ready());
        assertEquals(-1, reader.read()); assertFalse(reader.ready());
        reader.close(); reader.close();
        assertThrows(IOException.class, () -> reader.read(data, 0, 0));
        Reader unavailable = pool.open(new ByteArrayInputStream(new byte[0]) {
            @Override public int available() { return 0; }
        });
        assertFalse(unavailable.ready()); unavailable.close();
        Reader brokenAvailable = pool.open(new InputStream() {
            @Override public int read() { return -1; }
            @Override public int available() throws IOException { throw new IOException("unavailable"); }
        });
        assertFalse(brokenAvailable.ready()); brokenAvailable.close();
    }

    @Test void inputFailureAndZeroByteReadsRemainIoErrors() throws Exception {
        Utf8FrameReaders pool = new Utf8FrameReaders();
        Reader broken = pool.open(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("read failed"); }
        });
        assertEquals("read failed", assertThrows(IOException.class, broken::read).getMessage());
        broken.close();
        Reader zero = pool.open(new InputStream() {
            @Override public int read() { return 0; }
            @Override public int read(byte[] b, int off, int len) { return 0; }
        });
        assertEquals("Underlying input stream returned zero bytes", assertThrows(IOException.class, zero::read).getMessage());
        zero.close();
        final int[] closes = {0};
        Reader brokenClose = pool.open(new ByteArrayInputStream(new byte[0]) {
            @Override public void close() throws IOException { closes[0]++; throw new IOException("close failed"); }
        });
        assertEquals("close failed", assertThrows(IOException.class, brokenClose::close).getMessage());
        assertThrows(IOException.class, brokenClose::read);
        brokenClose.close(); assertEquals(1, closes[0]);
    }

    @Test void retryAfterReadFailurePreservesPartialUtf8LikeJdkReader() throws Exception {
        java.util.function.Supplier<InputStream> input = () -> new InputStream() {
            int call;
            @Override public int read() { throw new UnsupportedOperationException(); }
            @Override public int read(byte[] target, int offset, int length) throws IOException {
                switch (call++) {
                    case 0: target[offset] = (byte) 0xe4; return 1;
                    case 1: throw new IOException("temporary read failure");
                    case 2: target[offset] = (byte) 0xb8; target[offset + 1] = (byte) 0x80; return 2;
                    default: return -1;
                }
            }
        };
        try (Reader expected = new InputStreamReader(input.get(), StandardCharsets.UTF_8);
             Reader actual = new Utf8FrameReaders().open(input.get())) {
            assertEquals("temporary read failure", assertThrows(IOException.class, expected::read).getMessage());
            assertEquals("temporary read failure", assertThrows(IOException.class, actual::read).getMessage());
            assertEquals('一', expected.read()); assertEquals('一', actual.read());
            assertEquals(-1, expected.read()); assertEquals(-1, actual.read());
        }
    }
}
