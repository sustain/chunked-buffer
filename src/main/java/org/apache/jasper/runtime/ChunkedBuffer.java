package org.apache.jasper.runtime;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: vakopian
 * Date: 3/17/14
 * Time: 3:49 PM
 */
public class ChunkedBuffer {
    public static final int DEFAULT_INITIAL_CAPACITY = 512;
    public static final double DEFAULT_GROWTH_FACTOR = 1.5;
    public static final int MIN_CHUNK_SIZE = 16;
    public static final int DEFAULT_MAX_CHUNK_SIZE = 16 * 1024;

    private final int initialCapacity;
    private final double growthFactor;
    private final int maxChunkSize;

    private List<char[]> chunks;

    private int currentChunkIdx = -1;
    private char[] currentChunk = null;
    private int posInCurrentChunk;

    private int count;
    private int capacity;

    public ChunkedBuffer(String text) {
        this(DEFAULT_INITIAL_CAPACITY);
        append(text);
    }

    public ChunkedBuffer() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public ChunkedBuffer(int initialCapacity) {
        this(initialCapacity, DEFAULT_MAX_CHUNK_SIZE);
    }


    public ChunkedBuffer(int initialCapacity, int maxChunkSize) {
        this(initialCapacity, maxChunkSize, DEFAULT_GROWTH_FACTOR);
    }

    public ChunkedBuffer(int initialCapacity, int maxChunkSize, double growthFactor) {
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

        this.chunks = new ArrayList<char[]>();
        this.currentChunk = new char[initialCapacity];
        this.chunks.add(this.currentChunk);
        this.currentChunkIdx = 0;
        this.posInCurrentChunk = 0;
        this.count = 0;
        this.capacity = initialCapacity;
    }

    public void append(char character) {
        ensureCapacityInternal(count + 1);
        ensureCurrentChunkHasCapacity();
        currentChunk[posInCurrentChunk++] = character;
        this.count++;
    }

    public void append(String text) {
        append(text, 0, text.length());
    }

    public void append(String text, int start, int length) throws IllegalArgumentException {
        if (length <= 0) {
            return;
        }
        if (text == null) {
            throw new IllegalArgumentException("characters: may not be null.");
        }
        if (start < 0) {
            throw new IllegalArgumentException("start: points beyond end of array.");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length: specifies length in excess of array length.");
        }
        if (start > text.length() || length > text.length() || start + length < 0 || (start + length) > text.length()) {
            throw new StringIndexOutOfBoundsException("length: specifies length in excess of array length.");
        }

        ensureCapacityInternal(count + length);
        while (length > 0) {
            int charsToCopy = ensureCurrentChunkHasCapacity();
            if (charsToCopy > length) {
                charsToCopy = length;
            }
            if (charsToCopy > 0) {
                text.getChars(start, start + charsToCopy, currentChunk, posInCurrentChunk);
                start += charsToCopy;
                length -= charsToCopy;
                this.posInCurrentChunk += charsToCopy;
                this.count += charsToCopy;
            }
        }
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

    public void append(char[] characters, int start, int length) throws IllegalArgumentException {
        if (length <= 0) {
            return;
        }
        if (characters == null) {
            throw new IllegalArgumentException("characters: may not be null.");
        }
        if (start < 0) {
            throw new IllegalArgumentException("start: points beyond end of array.");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length: specifies length in excess of array length.");
        }
        if (start > characters.length || length > characters.length || start + length < 0 || (start + length) > characters.length) {
            throw new StringIndexOutOfBoundsException("length: specifies length in excess of array length.");
        }

        ensureCapacityInternal(count + length);
        while (length > 0) {
            int charsToCopy = ensureCurrentChunkHasCapacity();
            if (charsToCopy > length) {
                charsToCopy = length;
            }

            if (charsToCopy > 0) {
                System.arraycopy(characters, start, currentChunk, posInCurrentChunk, charsToCopy);
                start += charsToCopy;
                length -= charsToCopy;
                this.posInCurrentChunk += charsToCopy;
                this.count += charsToCopy;
            }
        }
    }

    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin)
    {
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
            char[] chunk = chunks.get(i);
            if (chunk.length >= srcBegin) {
                break;
            }
            srcBegin -= chunk.length;
        }
        int offset = dstBegin;
        int srcStart = srcBegin;
        for (; i < iEnd; i++) {
            char[] chunk = this.chunks.get(i);
            int charsToCopy = chunk.length - srcStart;
            if (charsToCopy > length) {
                charsToCopy = length;
            }
            System.arraycopy(chunk, srcStart, dst, offset, charsToCopy);
            offset += charsToCopy;
            length -= charsToCopy;
            srcStart = 0;
            if (length == 0) {
                break;
            }
        }
    }

    public char[] toArray() {
        char[] result = new char[count];
        if (count == 0) {
            return result;
        }
        assert (currentChunk != null);
        int offset = 0;
        for (char[] chunk : this.chunks) {
            if (chunk == currentChunk) {
                break;
            }
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        System.arraycopy(this.currentChunk, 0, result, offset, posInCurrentChunk);
        return result;
    }

    public String toString() {
        if (count == 0) {
            return "";
        }
        assert (currentChunk != null);
        StringBuilder sb = new StringBuilder(length());
        for (char[] chunk : this.chunks) {
            if (chunk == currentChunk) {
                break;
            }
            sb.append(chunk);
        }
        sb.append(currentChunk, 0, posInCurrentChunk);
        return sb.toString();
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
            char[] chunk = this.chunks.get(i);
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
            this.chunks.add(new char[size]);
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

    public Reader getReader() {
        return new ChunkedBufferReader(chunks, count);
    }

    public void writeOut(Writer writer) throws IOException, IllegalArgumentException {
        if (writer == null) {
            throw new IllegalArgumentException("writer: may not be null.");
        }
        if (count == 0) {
            return;
        }
        assert (currentChunk != null);
        for (char[] chunk : this.chunks) {
            if (chunk == this.currentChunk) {
                break;
            }
            writer.write(chunk);
        }
        writer.write(currentChunk, 0, posInCurrentChunk);
    }

    @SuppressWarnings("SynchronizeOnNonFinalField")
    private static class ChunkedBufferReader extends Reader {

        private final List<char[]> chunks;
        private final int count;

        private char[] currentChunk;
        private int currentChunkIdx = -1;
        private int posInCurrentChunk = 0;

        private char[] markedChunk;
        private int markedChunkIdx = -1;
        private int markedPos = 0;

        private ChunkedBufferReader(List<char[]> chunks, int count) {
            this.chunks = chunks;
            this.count = count;
            currentChunkIdx = chunks.size() == 0 ? -1 : 0;
            currentChunk = chunks.size() == 0 ? null : chunks.get(0);
        }

        private void ensureOpen() throws IOException {
            if (currentChunk == null) {
                throw new IOException("Stream closed");
            }
        }

        public int read() throws IOException {
            synchronized (lock) {
                ensureOpen();
                if (posInCurrentChunk >= count) {
                    return -1;
                }
                gotoNextReadableChunk();
                return currentChunk[posInCurrentChunk++];
            }
        }

        private int gotoNextReadableChunk() {
            int availableChars = currentChunk.length - posInCurrentChunk;
            if (availableChars > 0) {
                return availableChars;
            }
            currentChunkIdx++;
            currentChunk = chunks.get(currentChunkIdx);
            posInCurrentChunk = 0;
            return currentChunk.length;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            synchronized (lock) {
                ensureOpen();
                if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                    ((off + len) > cbuf.length) || ((off + len) < 0)) {
                    throw new IndexOutOfBoundsException();
                } else if (len == 0) {
                    return 0;
                }

                if (posInCurrentChunk >= count) {
                    return -1;
                }
                if (posInCurrentChunk + len > count) {
                    len = count - posInCurrentChunk;
                }
                if (len <= 0) {
                    return 0;
                }
                int remaining = len;
                while (remaining > 0) {
                    int charsToCopy = gotoNextReadableChunk();
                    if (charsToCopy > remaining) {
                        charsToCopy = remaining;
                    }
                    if (charsToCopy > 0) {
                        System.arraycopy(currentChunk, posInCurrentChunk, cbuf, off, charsToCopy);
                        off += charsToCopy;
                        remaining -= charsToCopy;
                        this.posInCurrentChunk += charsToCopy;
                    }
                }
                return len;
            }
        }

        public long skip(long n) throws IOException {
            synchronized (lock) {
                ensureOpen();
                if (posInCurrentChunk + n > count) {
                    n = count - posInCurrentChunk;
                }
                if (n < 0) {
                    return 0;
                }

                int remaining = (int) n;
                while (remaining > 0) {
                    int charsToAdvance = gotoNextReadableChunk();
                    if (charsToAdvance > remaining) {
                        charsToAdvance = remaining;
                    }
                    if (charsToAdvance > 0) {
                        remaining -= charsToAdvance;
                        this.posInCurrentChunk += remaining;
                    }
                }
                return n;
            }
        }

        public boolean ready() throws IOException {
            synchronized (lock) {
                ensureOpen();
                return (count - posInCurrentChunk) > 0;
            }
        }

        public boolean markSupported() {
            return true;
        }

        public void mark(int readAheadLimit) throws IOException {
            synchronized (lock) {
                ensureOpen();
                markedPos = posInCurrentChunk;
                markedChunk = currentChunk;
                markedChunkIdx = currentChunkIdx;
            }
        }

        public void reset() throws IOException {
            synchronized (lock) {
                ensureOpen();
                posInCurrentChunk = markedPos;
                currentChunk = markedChunk;
                currentChunkIdx = markedChunkIdx;
            }
        }


        @Override
        public void close() throws IOException {
            currentChunk = null;
        }
    }
}
