package dev.e2b.sdk.codeinterpreter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.Buffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Reuses decoding storage only after the previous frame's Reader has been closed. */
final class Utf8FrameReaders {
    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
    private final ByteBuffer bytes = ByteBuffer.allocate(8192);
    // Use Java 8 Buffer method descriptors even when compiled with a newer JDK.
    private final Buffer byteState = bytes;
    private final char[] pair = new char[2];
    private FrameReader active;
    private boolean inputEnded;
    private boolean finished;
    private boolean pendingSecondCharacter;

    Reader open(InputStream input) {
        if (input == null) {
            throw new NullPointerException("input");
        }
        if (active != null && !active.closed) {
            // AUTO_CLOSE_SOURCE=false or a custom mapper can retain an open Reader. Its
            // buffered state must remain independent of every later frame.
            return new InputStreamReader(input, StandardCharsets.UTF_8);
        }
        decoder.reset();
        byteState.clear();
        byteState.limit(0);
        inputEnded = false;
        finished = false;
        pendingSecondCharacter = false;
        active = new FrameReader(input);
        return active;
    }

    private final class FrameReader extends Reader {
        private final InputStream input;
        private volatile boolean closed;

        FrameReader(InputStream input) {
            this.input = input;
        }

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
        }

        @Override
        public int read(char[] target, int offset, int length) throws IOException {
            synchronized (lock) {
                ensureOpen();
                if (target == null) {
                    throw new NullPointerException("target");
                }
                if (offset < 0 || length < 0 || offset > target.length - length) {
                    throw new IndexOutOfBoundsException();
                }
                if (length == 0) {
                    return 0;
                }
                if (pendingSecondCharacter) {
                    target[offset] = pair[1];
                    pendingSecondCharacter = false;
                    return 1;
                }
                if (finished) {
                    return -1;
                }
                CharBuffer output = CharBuffer.wrap(target, offset, length);
                while (true) {
                    CoderResult result = decoder.decode(bytes, output, inputEnded);
                    if (result.isUnderflow() && inputEnded) {
                        // UTF-8 has no stateful characters to emit during flush.
                        decoder.flush(output);
                        finished = true;
                    }
                    int count = output.position() - offset;
                    if (count > 0) {
                        return count;
                    }
                    if (finished) {
                        return -1;
                    }
                    if (result.isOverflow()) {
                        // UTF-8 decoding can require two output slots before distinguishing a
                        // supplementary code point from a malformed four-byte sequence.
                        CharBuffer pairOutput = CharBuffer.wrap(pair);
                        decoder.decode(bytes, pairOutput, inputEnded);
                        target[offset] = pair[0];
                        pendingSecondCharacter = pairOutput.position() == 2;
                        return 1;
                    }
                    bytes.compact();
                    try {
                        int read = input.read(bytes.array(), bytes.position(), bytes.remaining());
                        if (read < 0) {
                            inputEnded = true;
                        } else if (read == 0) {
                            throw new IOException("Underlying input stream returned zero bytes");
                        } else {
                            byteState.position(bytes.position() + read);
                        }
                    } finally {
                        // Preserve pending bytes and read mode even when a caller retries after
                        // a transport IOException, as InputStreamReader's StreamDecoder does.
                        byteState.flip();
                    }
                }
            }
        }

        @Override
        public boolean ready() throws IOException {
            synchronized (lock) {
                ensureOpen();
                if (pendingSecondCharacter || bytes.hasRemaining()) {
                    return true;
                }
                try {
                    return input.available() > 0;
                } catch (IOException ignored) {
                    return false;
                }
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (lock) {
                if (!closed) {
                    closed = true;
                    input.close();
                }
            }
        }
    }
}
