package dev.e2b.sdk.codeinterpreter;

import okio.BufferedSource;

import java.io.IOException;
import java.io.InputStream;

/** One physical NDJSON line, read incrementally without buffering the complete line. */
final class NdjsonFrameInputStream extends InputStream {
    private final BufferedSource source;
    private boolean ended;

    NdjsonFrameInputStream(BufferedSource source) {
        this.source = source;
    }

    @Override
    public int read() throws IOException {
        if (ended || !source.request(1)) {
            ended = true;
            return -1;
        }
        int value = source.readByte() & 0xff;
        if (value == '\n') {
            ended = true;
            return -1;
        }
        return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (offset < 0 || length < 0 || offset > bytes.length - length) {
            throw new IndexOutOfBoundsException();
        }
        if (length == 0) {
            return 0;
        }
        if (ended || !source.request(1)) {
            ended = true;
            return -1;
        }
        // Search only already-buffered bytes. source.indexOf(LF) would buffer an entire huge line.
        long readLength = Math.min(8192, Math.min(length, source.getBuffer().size()));
        long newline = source.getBuffer().indexOf((byte) '\n', 0, readLength);
        if (newline == 0) {
            source.readByte();
            ended = true;
            return -1;
        }
        return source.read(bytes, offset, (int) (newline >= 0 ? newline : readLength));
    }

    void finish() throws IOException {
        // readTree can stop at the first root value when trailing-token checks are disabled.
        // Discard that frame's remainder just as the former String-based parser did.
        while (!ended && source.request(1)) {
            long length = Math.min(8192, source.getBuffer().size());
            long newline = source.getBuffer().indexOf((byte) '\n', 0, length);
            if (newline >= 0) {
                source.skip(newline + 1);
                ended = true;
            } else {
                source.skip(length);
            }
        }
        ended = true;
    }

    @Override
    public void close() {
        // Jackson owns its Reader, but the enclosing Response owns this shared source.
    }
}
