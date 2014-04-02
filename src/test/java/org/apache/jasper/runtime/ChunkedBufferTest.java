package org.apache.jasper.runtime;

import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by IntelliJ IDEA.
 * User: vakopian
 * Date: 3/17/14
 * Time: 3:49 PM
 */
public class ChunkedBufferTest {

    @Test
    public void testBufferChar() {
        ChunkedBuffer buf = new ChunkedBuffer(128);
        for (int i = 0; i < 1047; ++i) {
            buf.append('A');
        }
        assertEquals(1047, buf.length());
        assertEquals(1458, buf.capacity()); //1458=128*1.5^6 > 1047 > 128*1.5^5=972
    }

    @Test
    public void testBufferStringIntInt() {
        StringBuilder sb = new StringBuilder(10240);
        for (int i = 0; i < 10000; ++i) {
            sb.append('A');
        }

        ChunkedBuffer buf = new ChunkedBuffer(128);
        buf.append(sb.toString(), 100, 1047);
        assertEquals(1047, buf.length());
    }

    @Test
    public void testBufferCharArrayIntInt() {
        char[] array = new char[10470];
        for (int i = 0; i < array.length; ++i) {
            array[i] = 'A';
        }

        ChunkedBuffer buf = new ChunkedBuffer(128);
        buf.append(array, 765, 1047);
        assertEquals(1047, buf.length());
    }

    @Test
    public void testToArray() {
        char[] array = new char[1047];
        for (int i = 0; i < array.length; ++i) {
            array[i] = 'A';
        }

        ChunkedBuffer buf = new ChunkedBuffer(128);
        buf.append(array, 0, array.length);
        assertEquals(1047, buf.length());
        char[] result = buf.toArray();
        assertTrue(Arrays.equals(array, result));
    }

    @Test
    public void testToString() {
        StringBuilder sb = new StringBuilder(10240);
        for (int i = 0; i < 145; ++i) {
            sb.append("ABCDEFGHIJKLMN");
        }

        ChunkedBuffer buf = new ChunkedBuffer(128);
        buf.append(sb.toString(), 100, 1047);
        assertEquals(1047, buf.length());
        assertEquals(sb.substring(100, 1147), buf.toString());
    }

    @Test
    public void testToArray2() {
        ChunkedBuffer buf = new ChunkedBuffer(128);
        char[] array = new char[1047];
        char ch = 'A';
        for (int i = 0; i < 1047; ++i) {
            buf.append(ch);
            array[i] = ch;
        }

        assertEquals(1047, buf.length());
        char[] result = buf.toArray();
        assertEquals(1047, result.length);
        assertTrue(Arrays.equals(array, result));
    }

    @Test
    public void testToString2() {
        StringBuilder sb = new StringBuilder(10240);
        ChunkedBuffer buf = new ChunkedBuffer(128);
        String text = "ABCDEFGHIJKLMN";
        for (int i = 0; i < 145; ++i) {
            buf.append(text, 0, text.length());
            sb.append(text);
        }

        assertEquals(145 * text.length(), buf.length());
        assertEquals(sb.toString(), buf.toString());
    }

    @Test
    public void testToString3() {
        ChunkedBuffer buf = new ChunkedBuffer(128);
        assertEquals("", buf.toString());
        buf.clear();
        assertEquals("", buf.toString());
        buf.append("a");
        assertEquals("a", buf.toString());
    }

    @Test
    public void testClear() {
        StringBuilder sb = new StringBuilder(10240);
        for (int i = 0; i < 145; ++i) {
            sb.append("ABCDEFGHIJKLMN");
        }

        ChunkedBuffer buf = new ChunkedBuffer(128);
        buf.append(sb.toString(), 100, 1047);

        buf.clear();
        assertEquals(0, buf.length());
    }

    @Test
    public void testWriteOut() {
        StringWriter writer = new StringWriter();

        StringBuilder sb = new StringBuilder(10240);
        for (int i = 0; i < 145; ++i) {
            sb.append("ABCDEFGHIJKLMN");
        }

        ChunkedBuffer buf = new ChunkedBuffer(128);
        buf.append(sb.toString(), 100, 1047);
        assertEquals(1047, buf.length());
        assertEquals(sb.substring(100, 1147), buf.toString());

        try {
            buf.writeOut(writer);
        } catch (IOException e) {
            fail(e.toString());
        }
        assertEquals(sb.substring(100, 1147), writer.toString());

    }

    @Test
    public void testSetLength() {
        ChunkedBuffer active = new ChunkedBuffer();
        active.append("first one");
        String a = active.toString();
        active.setLength(0);
        active.append("second");
        String b = active.toString();
        active.setLength(3);
        assertEquals("ChunkedBuffer.setLength() overwrote string", "first one", a);
        assertEquals("second", b);
        assertEquals(3, active.length());
        assertTrue(active.capacity() > 3);
        active.append("tion");
        assertEquals("section", active.toString());
    }

    @Test
    public void testGetChars() {
        final int dstLen = 10;
        char[] dst = new char[dstLen];
        for (int i = 0; i < dstLen; i++) {
            dst[i] = 'Z';
        }
        final int srcBegin = 3;
        final int srcEnd = 9;
        final int dstBegin = 2;
        ChunkedBuffer cb = new ChunkedBuffer(2);
        cb.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        cb.getChars(srcBegin, srcEnd, dst, dstBegin);
        for (int i = 0; i < dstLen; ++i) {
            if (i >= dstBegin && i < dstBegin + (srcEnd - srcBegin)) {
                assertEquals('A' + srcBegin + (i- dstBegin), dst[i]);
            } else {
                assertEquals('Z', dst[i]);
            }
        }
    }

    @Test
    public void testGetCharsSrcEndLarger() {
        try {
            new ChunkedBuffer("abc").getChars(1, 0, new char[10], 0);
            fail("StringBuffer.getChars() must throw an exception if srcBegin > srcEnd");
        } catch (StringIndexOutOfBoundsException sioobe) {
        }
    }

    @Test
    public void testAppendLargeBuffer() {

        ChunkedBuffer cb = new ChunkedBuffer("");
        try {
            cb.append(new char[5], 1, Integer.MAX_VALUE);
            fail();
        } catch (StringIndexOutOfBoundsException sobe) {
            // Test passed
        } catch (OutOfMemoryError oome) {
            fail("Wrong exception thrown.");
        }

        ChunkedBuffer sb1 = new ChunkedBuffer("Some test StringBuffer");
        try {
            sb1.append(new char[25], 5, Integer.MAX_VALUE);
            fail();
        } catch (StringIndexOutOfBoundsException sobe) {
            // Test passed
        } catch (ArrayIndexOutOfBoundsException aioe) {
            fail("Wrong exception thrown.");
        }
    }
}
