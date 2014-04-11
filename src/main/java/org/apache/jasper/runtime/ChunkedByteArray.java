package org.apache.jasper.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: vakopian
 * Date: 3/17/14
 * Time: 3:49 PM
 */
public class ChunkedByteArray {
    public static final int DEFAULT_INITIAL_CAPACITY = 512;
    public static final double DEFAULT_GROWTH_FACTOR = 1.5;
    public static final int MIN_CHUNK_SIZE = 16;
    public static final int DEFAULT_MAX_CHUNK_SIZE = 16 * 1024;

    private final int initialCapacity;
    private final double growthFactor;
    private final int maxChunkSize;

    private List<byte[]> chunks;

    private int currentChunkIdx = -1;
    private byte[] currentChunk = null;
    private int posInCurrentChunk;

    private int count;
    private int capacity;

    public ChunkedByteArray(byte[] bytes) {
        this(DEFAULT_INITIAL_CAPACITY);
        append(bytes, 0, bytes.length);
    }

    public ChunkedByteArray() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public ChunkedByteArray(int initialCapacity) {
        this(initialCapacity, DEFAULT_MAX_CHUNK_SIZE);
    }


    public ChunkedByteArray(int initialCapacity, int maxChunkSize) {
        this(initialCapacity, maxChunkSize, DEFAULT_GROWTH_FACTOR);
    }

    public ChunkedByteArray(int initialCapacity, int maxChunkSize, double growthFactor) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be positive");
        }
        if (growthFactor < 1) {
            throw new IllegalArgumentException("growthFactor must be at least 1");
        }
        if (maxChunkSize < initialCapacity) {
            throw new IllegalArgumentException("maxChunkSize must be at least as large as initialCapacity");
        }
        this.initialCapacity = initialCapacity;
        this.growthFactor = growthFactor;
        this.maxChunkSize = maxChunkSize;

        this.chunks = new ArrayList<byte[]>();
        this.currentChunk = new byte[initialCapacity];
        this.chunks.add(this.currentChunk);
        this.currentChunkIdx = 0;
        this.posInCurrentChunk = 0;
        this.count = 0;
        this.capacity = initialCapacity;
    }

    public void append(byte b) {
        ensureCapacityInternal(count + 1);
        ensureCurrentChunkHasCapacity();
        currentChunk[posInCurrentChunk++] = b;
        this.count++;
    }

    private int ensureCurrentChunkHasCapacity() {
        int currentChunkCapacity = currentChunk.length - posInCurrentChunk;
        if (currentChunkCapacity > 0) {
            return currentChunkCapacity;
        }
        // go to the next chunk
        assert currentChunkIdx + 1 < chunks.size();
        posInCurrentChunk = 0;
        currentChunkIdx++;
        currentChunk = chunks.get(currentChunkIdx);
        return currentChunk.length;
    }

    public void append(byte[] bytes, int start, int length) throws IllegalArgumentException {
        if (length <= 0) {
            return;
        }
        if (bytes == null) {
            throw new IllegalArgumentException("bytes: may not be null.");
        }
        if (start < 0) {
            throw new IllegalArgumentException("start: points beyond end of array.");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length: specifies length in excess of array length.");
        }
        if (start > bytes.length || length > bytes.length || start + length < 0 || (start + length) > bytes.length) {
            throw new StringIndexOutOfBoundsException("length: specifies length in excess of array length.");
        }

        ensureCapacityInternal(count + length);
        while (length > 0) {
            int bytesToCopy = ensureCurrentChunkHasCapacity();
            if (bytesToCopy > length) {
                bytesToCopy = length;
            }

            if (bytesToCopy > 0) {
                System.arraycopy(bytes, start, currentChunk, posInCurrentChunk, bytesToCopy);
                start += bytesToCopy;
                length -= bytesToCopy;
                this.posInCurrentChunk += bytesToCopy;
                this.count += bytesToCopy;
            }
        }
    }

    public void getBytes(int srcBegin, int srcEnd, byte[] dst, int dstBegin) {
        if (srcBegin < 0)
            throw new StringIndexOutOfBoundsException(srcBegin);
        if (dstBegin < 0)
            throw new StringIndexOutOfBoundsException(dstBegin);
        if (dstBegin >= dst.length)
            throw new StringIndexOutOfBoundsException(dstBegin);
        if ((srcEnd < 0) || (srcEnd > count))
            throw new StringIndexOutOfBoundsException(srcEnd);
        if (srcBegin > srcEnd)
            throw new StringIndexOutOfBoundsException("srcBegin > srcEnd");
        if (srcBegin > count)
            throw new StringIndexOutOfBoundsException("srcBegin > length()");
        int length = srcEnd - srcBegin;
        if (length > count)
            throw new StringIndexOutOfBoundsException("srcEnd - srcBegin > length()");
        if (dstBegin + length > count)
            throw new StringIndexOutOfBoundsException("dstBegin + srcEnd - srcBegin > length()");
        if (length <= 0) {
            return;
        }

        int i = 0;
        int iEnd = chunks.size();
        for (; i < iEnd; i++) {
            byte[] chunk = chunks.get(i);
            if (chunk.length >= srcBegin) {
                break;
            }
            srcBegin -= chunk.length;
        }
        int offset = dstBegin;
        int srcStart = srcBegin;
        for (; i < iEnd; i++) {
            byte[] chunk = this.chunks.get(i);
            int bytesToCopy = chunk.length - srcStart;
            if (bytesToCopy > length) {
                bytesToCopy = length;
            }
            System.arraycopy(chunk, srcStart, dst, offset, bytesToCopy);
            offset += bytesToCopy;
            length -= bytesToCopy;
            srcStart = 0;
            if (length == 0) {
                break;
            }
        }
    }

    public byte[] toArray() {
        byte[] result = new byte[count];
        if (count == 0) {
            return result;
        }
        assert (currentChunk != null);
        int offset = 0;
        for (byte[] chunk : this.chunks) {
            if (chunk == currentChunk) {
                break;
            }
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        System.arraycopy(this.currentChunk, 0, result, offset, posInCurrentChunk);
        return result;
    }

    public int capacity() {
        return capacity;
    }

    public int length() {
        return this.count;
    }

    public void clear() {
        chunks.clear();
        currentChunk = null;
        currentChunkIdx = -1;
        capacity = 0;
        count = 0;
        posInCurrentChunk = 0;
    }

    public void setLength(int newLength) {
        if (newLength < 0)
            throw new IllegalArgumentException();
        ensureCapacityInternal(newLength);
        this.count = newLength;
        for (int i = 0, chunks1Size = this.chunks.size(); i < chunks1Size; i++) {
            byte[] chunk = this.chunks.get(i);
            this.currentChunk = chunk;
            this.posInCurrentChunk = newLength;
            this.currentChunkIdx = i;
            if (chunk.length >= newLength) {
                break;
            }
            newLength -= chunk.length;
        }
    }

    public void trimToSize() {
        chunks.subList(currentChunkIdx + 1, chunks.size()).clear();
    }

    private void ensureCapacityInternal(int newCapacity) {
        if (newCapacity < 0)
            throw new IllegalArgumentException();
        while (newCapacity >= this.capacity) {
            // allocate a new chunk
            int size = capacity == 0 ? this.initialCapacity : (int) (capacity * growthFactor) - capacity;
            if (size < MIN_CHUNK_SIZE) {
                size = MIN_CHUNK_SIZE;
            }
            if (size > maxChunkSize) {
                size = maxChunkSize;
            }
            this.chunks.add(new byte[size]);
            this.capacity += size;
        }
        if (currentChunk == null && chunks.size() > 0) {
            currentChunkIdx = 0;
            currentChunk = chunks.get(0);
            posInCurrentChunk = 0;
            count = 0;
        }
    }

    public int getUnused() {
        int sz = currentChunk.length - posInCurrentChunk;
        for (int i = currentChunkIdx + 1, chunksSize = chunks.size(); i < chunksSize; i++) {
            sz += chunks.get(i).length;
        }
        return sz;
    }

    public InputStream getInputStream() {
        return new ChunkedByteBufferInputStream(chunks, count);
    }

    public void writeOut(OutputStream outputStream) throws IOException, IllegalArgumentException {
        if (outputStream == null) {
            throw new IllegalArgumentException("outputStream: may not be null.");
        }
        if (count == 0) {
            return;
        }
        assert (currentChunk != null);
        for (byte[] chunk : this.chunks) {
            if (chunk == this.currentChunk) {
                break;
            }
            outputStream.write(chunk);
        }
        outputStream.write(currentChunk, 0, posInCurrentChunk);
    }

    private static class ChunkedByteBufferInputStream extends InputStream {
        private final List<byte[]> chunks;
        private final int count;
        private int pos = 0;

        private byte[] currentChunk;
        private int currentChunkIdx = -1;
        private int posInCurrentChunk = 0;

        private byte[] markedChunk;
        private int markedChunkIdx = -1;
        private int markedPos = 0;
        private int markedPosInCurrentChunk = 0;

        private ChunkedByteBufferInputStream(List<byte[]> chunks, int count) {
            this.chunks = chunks;
            this.count = count;
            markedChunkIdx = currentChunkIdx = chunks.size() == 0 ? -1 : 0;
            markedChunk = currentChunk = chunks.size() == 0 ? null : chunks.get(0);
        }

        private void ensureOpen() throws IOException {
            if (isClosed()) {
                throw new IOException("Stream closed");
            }
        }

        private boolean isClosed() {
            return currentChunk == null;
        }

        public int read() throws IOException {
            ensureOpen();
            if (pos >= count) {
                return -1;
            }
            gotoNextReadableChunk();
            pos++;
            return currentChunk[posInCurrentChunk++];
        }

        private int gotoNextReadableChunk() {
            int availableBytes = currentChunk.length - posInCurrentChunk;
            if (availableBytes > 0) {
                return availableBytes;
            }
            currentChunkIdx++;
            currentChunk = chunks.get(currentChunkIdx);
            posInCurrentChunk = 0;
            return currentChunk.length;
        }

        @Override
        public int read(byte[] cbuf, int off, int len) throws IOException {
            ensureOpen();
            if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                    ((off + len) > cbuf.length) || ((off + len) < 0)) {
                throw new IndexOutOfBoundsException();
            } else if (len == 0) {
                return 0;
            }

            if (pos >= count) {
                return -1;
            }
            if (pos + len > count) {
                len = count - posInCurrentChunk;
            }
            if (len <= 0) {
                return 0;
            }
            int remaining = len;
            while (remaining > 0) {
                int bytesToCopy = gotoNextReadableChunk();
                if (bytesToCopy > remaining) {
                    bytesToCopy = remaining;
                }
                if (bytesToCopy > 0) {
                    System.arraycopy(currentChunk, posInCurrentChunk, cbuf, off, bytesToCopy);
                    off += bytesToCopy;
                    remaining -= bytesToCopy;
                    this.posInCurrentChunk += bytesToCopy;
                    this.pos += bytesToCopy;
                }
            }
            return len;
        }

        public long skip(long n) throws IOException {
            ensureOpen();
            if (pos + n > count) {
                n = count - pos;
            }
            if (n < 0) {
                return 0;
            }

            int remaining = (int) n;
            while (remaining > 0) {
                int bytesToAdvance = gotoNextReadableChunk();
                if (bytesToAdvance > remaining) {
                    bytesToAdvance = remaining;
                }
                if (bytesToAdvance > 0) {
                    remaining -= bytesToAdvance;
                    this.posInCurrentChunk += remaining;
                    this.pos += remaining;
                }
            }
            return n;
        }

        public boolean ready() throws IOException {
            ensureOpen();
            return (count - pos) > 0;
        }

        public boolean markSupported() {
            return true;
        }

        public void mark(int readAheadLimit) {
            if (isClosed()) {
                return;
            }
            markedPos = pos;
            markedPosInCurrentChunk = posInCurrentChunk;
            markedChunk = currentChunk;
            markedChunkIdx = currentChunkIdx;
        }

        public void reset() throws IOException {
            ensureOpen();
            pos = markedPos;
            posInCurrentChunk = markedPosInCurrentChunk;
            currentChunk = markedChunk;
            currentChunkIdx = markedChunkIdx;
        }


        @Override
        public void close() throws IOException {
            currentChunk = null;
        }
    }
}
