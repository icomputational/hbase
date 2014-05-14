/**
 * Copyright 2010 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.thrift.HBaseNiftyThriftServer;
import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.io.WritableUtils;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TMemoryBuffer;
import org.apache.thrift.transport.TMemoryInputTransport;

import sun.misc.Unsafe;

import com.facebook.nifty.header.protocol.TFacebookCompactProtocol;
import com.facebook.swift.codec.ThriftCodec;

/**
 * Utility class that handles byte arrays, conversions to/from other types,
 * comparisons, hash code generation, manufacturing keys for HashMaps or
 * HashSets, etc.
 */
public class Bytes {

  private static final Log LOG = LogFactory.getLog(Bytes.class);

  /**
   * Size of boolean in bytes
   */
  public static final int SIZEOF_BOOLEAN = Byte.SIZE / Byte.SIZE;

  /**
   * Size of byte in bytes
   */
  public static final int SIZEOF_BYTE = SIZEOF_BOOLEAN;

  /**
   * Size of char in bytes
   */
  public static final int SIZEOF_CHAR = Character.SIZE / Byte.SIZE;

  /**
   * Size of double in bytes
   */
  public static final int SIZEOF_DOUBLE = Double.SIZE / Byte.SIZE;

  /**
   * Size of float in bytes
   */
  public static final int SIZEOF_FLOAT = Float.SIZE / Byte.SIZE;

  /**
   * Size of int in bytes
   */
  public static final int SIZEOF_INT = Integer.SIZE / Byte.SIZE;

  /**
   * Size of long in bytes
   */
  public static final int SIZEOF_LONG = Long.SIZE / Byte.SIZE;

  /**
   * Size of short in bytes
   */
  public static final int SIZEOF_SHORT = Short.SIZE / Byte.SIZE;


  /**
   * Estimate of size cost to pay beyond payload in jvm for instance of byte [].
   * Estimate based on study of jhat and jprofiler numbers.
   */
  // JHat says BU is 56 bytes.
  // SizeOf which uses java.lang.instrument says 24 bytes. (3 longs?)
  public static final int ESTIMATED_HEAP_TAX = 16;

  final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();


  /**
   * Byte array comparator class.
   */
  public static class ByteArrayComparator implements RawComparator<byte []> {
    /**
     * Constructor
     */
    public ByteArrayComparator() {
      super();
    }
    @Override
    public int compare(byte [] left, byte [] right) {
      return compareTo(left, right);
    }
    @Override
    public int compare(byte [] b1, int s1, int l1, byte [] b2, int s2, int l2) {
      return compareTo(b1, s1, l1, b2, s2, l2);
    }
  }

  /**
   * Pass this to TreeMaps where byte [] are keys.
   */
  public static final Comparator<byte []> BYTES_COMPARATOR =
    new ByteArrayComparator();

  /**
   * Use comparing byte arrays, byte-by-byte
   */
  public static final RawComparator<byte []> BYTES_RAWCOMPARATOR =
    new ByteArrayComparator();

  public static final Comparator<ByteBuffer> BYTE_BUFFER_COMPARATOR =
      new Comparator<ByteBuffer>() {
        @Override
        public int compare(ByteBuffer left, ByteBuffer right) {
          int lpos = left.position();
          int rpos = right.position();
          return compareTo(left.array(), left.arrayOffset() + lpos, left.limit() - lpos,
              right.array(), right.arrayOffset() + rpos, right.limit() - rpos);
        }
      };

  /**
   * Read byte-array written with a WritableableUtils.vint prefix.
   * @param in Input to read from.
   * @return byte array read off <code>in</code>
   * @throws IOException e
   */
  public static byte [] readByteArray(final DataInput in)
  throws IOException {
    int len = WritableUtils.readVInt(in);
    if (len < 0) {
      throw new NegativeArraySizeException(Integer.toString(len));
    }
    byte [] result = new byte[len];
    in.readFully(result, 0, len);
    return result;
  }

  /**
   * Read byte-array written with a WritableableUtils.vint prefix.
   * IOException is converted to a RuntimeException.
   * @param in Input to read from.
   * @return byte array read off <code>in</code>
   */
  public static byte [] readByteArrayThrowsRuntime(final DataInput in) {
    try {
      return readByteArray(in);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Write byte-array with a WritableableUtils.vint prefix.
   * @param out output stream to be written to
   * @param b array to write
   * @throws IOException e
   */
  public static void writeByteArray(final DataOutput out, final byte [] b)
  throws IOException {
    if(b == null) {
      WritableUtils.writeVInt(out, 0);
    } else {
      writeByteArray(out, b, 0, b.length);
    }
  }

  /**
   * Write byte-array to out with a vint length prefix.
   * @param out output stream
   * @param b array
   * @param offset offset into array
   * @param length length past offset
   * @throws IOException e
   */
  public static void writeByteArray(final DataOutput out, final byte [] b,
      final int offset, final int length)
  throws IOException {
    WritableUtils.writeVInt(out, length);
    out.write(b, offset, length);
  }

  /**
   * Write byte-array from src to tgt with a vint length prefix.
   * @param tgt target array
   * @param tgtOffset offset into target array
   * @param src source array
   * @param srcOffset source offset
   * @param srcLength source length
   * @return New offset in src array.
   */
  public static int writeByteArray(final byte [] tgt, final int tgtOffset,
      final byte [] src, final int srcOffset, final int srcLength) {
    byte [] vint = vintToBytes(srcLength);
    System.arraycopy(vint, 0, tgt, tgtOffset, vint.length);
    int offset = tgtOffset + vint.length;
    System.arraycopy(src, srcOffset, tgt, offset, srcLength);
    return offset + srcLength;
  }

  /**
   * Put bytes at the specified byte array position.
   * @param tgtBytes the byte array
   * @param tgtOffset position in the array
   * @param srcBytes array to write out
   * @param srcOffset source offset
   * @param srcLength source length
   * @return incremented offset
   */
  public static int putBytes(byte[] tgtBytes, int tgtOffset, byte[] srcBytes,
      int srcOffset, int srcLength) {
    System.arraycopy(srcBytes, srcOffset, tgtBytes, tgtOffset, srcLength);
    return tgtOffset + srcLength;
  }

  /**
   * Write a single byte out to the specified byte array position.
   * @param bytes the byte array
   * @param offset position in the array
   * @param b byte to write out
   * @return incremented offset
   */
  public static int putByte(byte[] bytes, int offset, byte b) {
    bytes[offset] = b;
    return offset + 1;
  }

  /**
   * Returns a new byte array, copied from the passed ByteBuffer. Starts from the array offset
   * of the buffer and copies bytes to the limit of the buffer.
   * @param bb A ByteBuffer
   * @return the byte array
   */
  public static byte[] toBytes(ByteBuffer bb) {
    int length = bb.limit();
    byte [] result = new byte[length];
    System.arraycopy(bb.array(), bb.arrayOffset(), result, 0, length);
    return result;
  }

  /**
   * Similar to {@link #toBytes(ByteBuffer)}, except return the underlying
   * array. This is an optimization so that we don't create another copy of the
   * underlying byte array, if possible.
   *
   * @param reuseUnderlyingArray
   * @return
   */
  public static byte[] toBytes(ByteBuffer bb, boolean reuseUnderlyingArray) {
    // Return the underlying the ByteBuffer, if we want to reuse it and it is
    // possible to do so (The offset is 0, and the limit of the BB is equal
    // to the length of the underlying array).
    if (reuseUnderlyingArray &&
      (bb.arrayOffset() == 0 && bb.limit() == bb.array().length)) {
      return bb.array();
    }
    // Return a new byte array.
    return toBytes(bb);
  }

  /**
   * Returns a new byte array, copied from the passed ByteBuffer. Starts from the current position
   * in the buffer and copies all the remaining bytes to the limit of the buffer.
   * @param bb A ByteBuffer
   * @return the byte array
   */
  public static byte[] toBytesRemaining(ByteBuffer bb) {
    int length = bb.remaining();
    byte [] result = new byte[length];
    System.arraycopy(bb.array(), bb.arrayOffset() + bb.position(), result, 0, length);
    return result;
  }

  /**
   * @param b Presumed UTF-8 encoded byte array.
   * @return String made from <code>b</code>
   */
  public static String toString(final byte[] b) {
    if (b == null) {
      return null;
    }
    return toString(b, 0, b.length);
  }

  /**
   * Joins two byte arrays together using a separator.
   * @param b1 The first byte array.
   * @param sep The separator to use.
   * @param b2 The second byte array.
   */
  public static String toString(final byte [] b1,
                                String sep,
                                final byte [] b2) {
    return toString(b1, 0, b1.length) + sep + toString(b2, 0, b2.length);
  }

  /**
   * This method will convert utf8 encoded bytes into a string. If
   * an UnsupportedEncodingException occurs, this method will eat it
   * and return null instead.
   *
   * @param b Presumed UTF-8 encoded byte array.
   * @param off offset into array
   * @param len length of utf-8 sequence
   * @return String made from <code>b</code> or null
   */
  public static String toString(final byte [] b, int off, int len) {
    if (b == null) {
      return null;
    }
    if (len == 0) {
      return "";
    }
    try {
      return new String(b, off, len, HConstants.UTF8_ENCODING);
    } catch (UnsupportedEncodingException e) {
      LOG.error("UTF-8 not supported?", e);
      return null;
    }
  }

  /**
   * Write a printable representation of a byte array.
   *
   * @param b byte array
   * @return string
   * @see #toStringBinary(byte[], int, int)
   */
  public static String toStringBinary(final byte [] b) {
    if (b == null)
      return "null";
    return toStringBinary(b, 0, b.length);
  }

  /**
   * Converts the given byte buffer, from its array offset to its limit, to
   * a string. The position and the mark are ignored.
   *
   * @param buf a byte buffer
   * @return a string representation of the buffer's binary contents
   */
  public static String toStringBinary(ByteBuffer buf) {
    if (buf == null)
      return "null";
    return toStringBinary(buf.array(), buf.arrayOffset(), buf.limit());
  }

  /**
   * Similar to {@link #toStringBinary(byte[])}, but converts the portion of the buffer from the
   * current position to the limit to string.
   *
   * @param buf a byte buffer
   * @return a string representation of the buffer's remaining contents
   */
  public static String toStringBinaryRemaining(ByteBuffer buf) {
    if (buf == null) {
      return "null";
    }
    int offset = buf.arrayOffset();
    int pos = buf.position();
    return toStringBinary(buf.array(), offset + pos, buf.limit() - pos);
  }

  /**
   * Write a printable representation of a byte array. Non-printable
   * characters are hex escaped in the format \\x%02X, eg:
   * \x00 \x05 etc
   *
   * @param b array to write out
   * @param off offset to start at
   * @param len length to write
   * @return string output
   */
  public static String toStringBinary(final byte [] b, int off, int len) {
    StringBuilder result = new StringBuilder();
    try {
      String first = new String(b, off, len, "ISO-8859-1");
      for (int i = 0; i < first.length() ; ++i ) {
        int ch = first.charAt(i) & 0xFF;
        if ( (ch >= '0' && ch <= '9')
            || (ch >= 'A' && ch <= 'Z')
            || (ch >= 'a' && ch <= 'z')
            || " `~!@#$%^&*()-_=+[]{}\\|;:'\",.<>/?".indexOf(ch) >= 0 ) {
          result.append(first.charAt(i));
        } else {
          result.append(String.format("\\x%02X", ch));
        }
      }
    } catch (UnsupportedEncodingException e) {
      LOG.error("ISO-8859-1 not supported?", e);
    }
    return result.toString();
  }

  private static boolean isHexDigit(char c) {
    return
        (c >= 'A' && c <= 'F') ||
        (c >= '0' && c <= '9');
  }

  /**
   * Takes a ASCII digit in the range A-F0-9 and returns
   * the corresponding integer/ordinal value.
   * @param ch  The hex digit.
   * @return The converted hex value as a byte.
   */
  public static byte toBinaryFromHex(byte ch) {
    if ( ch >= 'A' && ch <= 'F' )
      return (byte) ((byte)10 + (byte) (ch - 'A'));
    // else
    return (byte) (ch - '0');
  }

  public static byte [] toBytesBinary(String in) {
    // this may be bigger than we need, but lets be safe.
    byte [] b = new byte[in.length()];
    int size = 0;
    for (int i = 0; i < in.length(); ++i) {
      char ch = in.charAt(i);
      if (ch == '\\') {
        // begin hex escape:
        char next = in.charAt(i+1);
        if (next != 'x') {
          // invalid escape sequence, ignore this one.
          b[size++] = (byte)ch;
          continue;
        }
        // ok, take next 2 hex digits.
        char hd1 = in.charAt(i+2);
        char hd2 = in.charAt(i+3);

        // they need to be A-F0-9:
        if (!isHexDigit(hd1) ||
            !isHexDigit(hd2)) {
          // bogus escape code, ignore:
          continue;
        }
        // turn hex ASCII digit -> number
        byte d = (byte) ((toBinaryFromHex((byte)hd1) << 4) + toBinaryFromHex((byte)hd2));

        b[size++] = d;
        i += 3; // skip 3
      } else {
        b[size++] = (byte) ch;
      }
    }
    // resize:
    byte [] b2 = new byte[size];
    System.arraycopy(b, 0, b2, 0, size);
    return b2;
  }

  /**
   * Converts a string to a UTF-8 byte array.
   * @param s string
   * @return the byte array
   */
  public static byte[] toBytes(String s) {
    try {
      return s.getBytes(HConstants.UTF8_ENCODING);
    } catch (UnsupportedEncodingException e) {
      LOG.error("UTF-8 not supported?", e);
      return null;
    }
  }


  public static String bytesToHex(byte[] bytes, int offset, int length) {
    char[] hexChars = new char[length * 2];
    for (int j = 0; j < length; j++) {
      int v = bytes[offset + j] & 0xFF;
      hexChars[j * 2] = hexArray[v >>> 4];
      hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
  }

  public static byte[] hexToBytes(String s) {
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character
          .digit(s.charAt(i + 1), 16));
    }
    return data;
  }

  public static byte[] string64ToBytes(String s) {
    return Base64.decode(s);
  }

  public static String bytesToString64(byte[] bytes, int offset, int length) {
    return Base64.encodeBytes(bytes, offset, length);
  }


  /**
   * Convert a boolean to a byte array. True becomes -1
   * and false becomes 0.
   *
   * @param b value
   * @return <code>b</code> encoded in a byte array.
   */
  public static byte [] toBytes(final boolean b) {
    return new byte[] { b ? (byte) -1 : (byte) 0 };
  }

  /**
   * Reverses {@link #toBytes(boolean)}
   * @param b array
   * @return True or false.
   */
  public static boolean toBoolean(final byte [] b) {
    if (b.length != 1) {
      throw new IllegalArgumentException("Array has wrong size: " + b.length);
    }
    return b[0] != (byte) 0;
  }

  /**
   * Convert a long value to a byte array using big-endian.
   *
   * @param val value to convert
   * @return the byte array
   */
  public static byte[] toBytes(long val) {
    byte [] b = new byte[8];
    for (int i = 7; i > 0; i--) {
      b[i] = (byte) val;
      val >>>= 8;
    }
    b[0] = (byte) val;
    return b;
  }

  /**
   * Converts a Long value to a byte array using big-endian.
   */
  public static byte[] toBytes(Long val) {
    return toBytes(val.longValue());
  }

  /**
   * Converts a byte array to a long value. Reverses
   * {@link #toBytes(long)}
   * @param bytes array
   * @return the long value
   */
  public static long toLong(byte[] bytes) {
    return toLong(bytes, 0);
  }

  /**
   * Converts a byte array to a long value. Assumes there will be
   * {@link #SIZEOF_LONG} bytes available.
   *
   * @param bytes bytes
   * @param offset offset
   * @return the long value
   */
  public static long toLong(byte[] bytes, int offset) {
    if (offset + SIZEOF_LONG > bytes.length) {
      throw explainWrongLengthOrOffset(bytes, offset, SIZEOF_LONG, SIZEOF_LONG);
    }
    long l = 0;
    for (int i = offset; i < offset + SIZEOF_LONG; i++) {
      l <<= 8;
      l ^= bytes[i] & 0xFF;
    }
    return l;
  }

  /**
   * Converts a byte array to a long value.
   *
   * @param bytes array of bytes
   * @param offset offset into array
   * @param length length of data (must be {@link #SIZEOF_LONG})
   * @return the long value
   * @throws IllegalArgumentException if length is not {@link #SIZEOF_LONG} or
   * if there's not enough room in the array at the offset indicated.
   */
  public static long toLong(byte[] bytes, int offset, final int length) {
    if (length != SIZEOF_LONG) {
      throw explainWrongLengthOrOffset(bytes, offset, length, SIZEOF_LONG);
    }
    return toLong(bytes, offset);
  }

  private static IllegalArgumentException
    explainWrongLengthOrOffset(final byte[] bytes,
                               final int offset,
                               final int length,
                               final int expectedLength) {
    String reason;
    if (length != expectedLength) {
      reason = "Wrong length: " + length + ", expected " + expectedLength;
    } else {
     reason = "offset (" + offset + ") + length (" + length + ") exceed the"
        + " capacity of the array: " + bytes.length;
    }
    return new IllegalArgumentException(reason);
  }

  /**
   * Put a long value out to the specified byte array position.
   * @param bytes the byte array
   * @param offset position in the array
   * @param val long to write out
   * @return incremented offset
   * @throws IllegalArgumentException if the byte array given doesn't have
   * enough room at the offset specified.
   */
  public static int putLong(byte[] bytes, int offset, long val) {
    if (bytes.length - offset < SIZEOF_LONG) {
      throw new IllegalArgumentException("Not enough room to put a long at"
          + " offset " + offset + " in a " + bytes.length + " byte array");
    }
    for(int i = offset + 7; i > offset; i--) {
      bytes[i] = (byte) val;
      val >>>= 8;
    }
    bytes[offset] = (byte) val;
    return offset + SIZEOF_LONG;
  }

  /**
   * Presumes float encoded as IEEE 754 floating-point "single format"
   * @param bytes byte array
   * @return Float made from passed byte array.
   */
  public static float toFloat(byte [] bytes) {
    return toFloat(bytes, 0);
  }

  /**
   * Presumes float encoded as IEEE 754 floating-point "single format"
   * @param bytes array to convert
   * @param offset offset into array
   * @return Float made from passed byte array.
   */
  public static float toFloat(byte [] bytes, int offset) {
    return Float.intBitsToFloat(toInt(bytes, offset, SIZEOF_FLOAT));
  }

  /**
   * @param bytes byte array
   * @param offset offset to write to
   * @param f float value
   * @return New offset in <code>bytes</code>
   */
  public static int putFloat(byte [] bytes, int offset, float f) {
    return putInt(bytes, offset, Float.floatToRawIntBits(f));
  }

  /**
   * @param f float value
   * @return the float represented as byte []
   */
  public static byte [] toBytes(final float f) {
    // Encode it as int
    return Bytes.toBytes(Float.floatToRawIntBits(f));
  }

  /**
   * @param bytes byte array
   * @return Return double made from passed bytes.
   */
  public static double toDouble(final byte [] bytes) {
    return toDouble(bytes, 0);
  }

  /**
   * @param bytes byte array
   * @param offset offset where double is
   * @return Return double made from passed bytes.
   */
  public static double toDouble(final byte [] bytes, final int offset) {
    return Double.longBitsToDouble(toLong(bytes, offset, SIZEOF_DOUBLE));
  }

  /**
   * @param bytes byte array
   * @param offset offset to write to
   * @param d value
   * @return New offset into array <code>bytes</code>
   */
  public static int putDouble(byte [] bytes, int offset, double d) {
    return putLong(bytes, offset, Double.doubleToLongBits(d));
  }

  /**
   * Serialize a double as the IEEE 754 double format output. The resultant
   * array will be 8 bytes long.
   *
   * @param d value
   * @return the double represented as byte []
   */
  public static byte [] toBytes(final double d) {
    // Encode it as a long
    return Bytes.toBytes(Double.doubleToRawLongBits(d));
  }

  /**
   * Convert an int value to a byte array
   * @param val value
   * @return the byte array
   */
  public static byte[] toBytes(int val) {
    byte [] b = new byte[4];
    for(int i = 3; i > 0; i--) {
      b[i] = (byte) val;
      val >>>= 8;
    }
    b[0] = (byte) val;
    return b;
  }

  /**
   * Converts a byte array to an int value
   * @param bytes byte array
   * @return the int value
   */
  public static int toInt(byte[] bytes) {
    return toInt(bytes, 0, SIZEOF_INT);
  }

  /**
   * Converts a byte array to an int value
   * @param bytes byte array
   * @param offset offset into array
   * @return the int value
   */
  public static int toInt(byte[] bytes, int offset) {
    return toInt(bytes, offset, SIZEOF_INT);
  }

  /**
   * Converts a byte array to an int value
   * @param bytes byte array
   * @param offset offset into array
   * @param length length of int (has to be {@link #SIZEOF_INT})
   * @return the int value
   * @throws IllegalArgumentException if length is not {@link #SIZEOF_INT} or
   * if there's not enough room in the array at the offset indicated.
   */
  public static int toInt(byte[] bytes, int offset, final int length) {
    if (length != SIZEOF_INT || offset + length > bytes.length) {
      throw explainWrongLengthOrOffset(bytes, offset, length, SIZEOF_INT);
    }
    int n = 0;
    for(int i = offset; i < (offset + length); i++) {
      n <<= 8;
      n ^= bytes[i] & 0xFF;
    }
    return n;
  }

  /**
   * Put an int value out to the specified byte array position.
   * @param bytes the byte array
   * @param offset position in the array
   * @param val int to write out
   * @return incremented offset
   * @throws IllegalArgumentException if the byte array given doesn't have
   * enough room at the offset specified.
   */
  public static int putInt(byte[] bytes, int offset, int val) {
    if (bytes.length - offset < SIZEOF_INT) {
      throw new IllegalArgumentException("Not enough room to put an int at"
          + " offset " + offset + " in a " + bytes.length + " byte array");
    }
    for(int i= offset + 3; i > offset; i--) {
      bytes[i] = (byte) val;
      val >>>= 8;
    }
    bytes[offset] = (byte) val;
    return offset + SIZEOF_INT;
  }

  /**
   * Converts a char value to a byte array of {@link #SIZEOF_CHAR} bytes long.
   *
   * @param val value
   * @return the byte array
   */
  public static byte[] toBytes(char val) {
    return toBytes((short) val);
  }

  /**
   * Converts a Character value to a byte array of {@link #SIZEOF_CHAR} bytes
   * long.
   *
   * @param val value
   * @return the byte array
   */
  public static byte[] toBytes(Character val) {
    return toBytes(val.charValue());
  }

  /**
   * Converts a byte array to a char value
   */
  public static char toChar(byte[] bytes) {
    return (char) Bytes.toShort(bytes);
  }

  /**
   * Convert a short value to a byte array of {@link #SIZEOF_SHORT} bytes long.
   *
   * @param val value
   * @return the byte array
   */
  public static byte[] toBytes(short val) {
    byte[] b = new byte[SIZEOF_SHORT];
    b[1] = (byte) val;
    val >>= 8;
    b[0] = (byte) val;
    return b;
  }

  /**
   * Converts a byte array to a short value
   * @param bytes byte array
   * @return the short value
   */
  public static short toShort(byte[] bytes) {
    return toShort(bytes, 0, SIZEOF_SHORT);
  }

  /**
   * Converts a byte array to a short value
   * @param bytes byte array
   * @param offset offset into array
   * @return the short value
   */
  public static short toShort(byte[] bytes, int offset) {
    return toShort(bytes, offset, SIZEOF_SHORT);
  }

  /**
   * Converts a byte array to a short value
   * @param bytes byte array
   * @param offset offset into array
   * @param length length, has to be {@link #SIZEOF_SHORT}
   * @return the short value
   * @throws IllegalArgumentException if length is not {@link #SIZEOF_SHORT}
   * or if there's not enough room in the array at the offset indicated.
   */
  public static short toShort(byte[] bytes, int offset, final int length) {
    if (length != SIZEOF_SHORT || offset + length > bytes.length) {
      throw explainWrongLengthOrOffset(bytes, offset, length, SIZEOF_SHORT);
    }
    short n = 0;
    n ^= bytes[offset] & 0xFF;
    n <<= 8;
    n ^= bytes[offset+1] & 0xFF;
    return n;
  }

  public static byte[] getBytes(ByteBuffer buf) {
    if (buf == null) {
      return HConstants.EMPTY_BYTE_ARRAY;
    }

    if (buf.arrayOffset() == 0 && buf.position() == 0) {
      byte[] arr = buf.array();
      if (buf.limit() == arr.length) {
        // We already have the exact array we need, just return it.
        return arr;
      }
    }

    int savedPos = buf.position();
    byte [] newBytes = new byte[buf.remaining()];
    buf.get(newBytes);
    buf.position(savedPos);
    return newBytes;
  }

  /**
   * Put a short value out to the specified byte array position.
   * @param bytes the byte array
   * @param offset position in the array
   * @param val short to write out
   * @return incremented offset
   * @throws IllegalArgumentException if the byte array given doesn't have
   * enough room at the offset specified.
   */
  public static int putShort(byte[] bytes, int offset, short val) {
    if (bytes.length - offset < SIZEOF_SHORT) {
      throw new IllegalArgumentException("Not enough room to put a short at"
          + " offset " + offset + " in a " + bytes.length + " byte array");
    }
    bytes[offset+1] = (byte) val;
    val >>= 8;
    bytes[offset] = (byte) val;
    return offset + SIZEOF_SHORT;
  }

  /**
   * @param vint Integer to make a vint of.
   * @return Vint as bytes array.
   */
  public static byte [] vintToBytes(final long vint) {
    long i = vint;
    int size = WritableUtils.getVIntSize(i);
    byte [] result = new byte[size];
    int offset = 0;
    if (i >= -112 && i <= 127) {
      result[offset] = (byte) i;
      return result;
    }

    int len = -112;
    if (i < 0) {
      i ^= -1L; // take one's complement'
      len = -120;
    }

    long tmp = i;
    while (tmp != 0) {
      tmp = tmp >> 8;
      len--;
    }

    result[offset++] = (byte) len;

    len = (len < -120) ? -(len + 120) : -(len + 112);

    for (int idx = len; idx != 0; idx--) {
      int shiftbits = (idx - 1) * 8;
      long mask = 0xFFL << shiftbits;
      result[offset++] = (byte)((i & mask) >> shiftbits);
    }
    return result;
  }

  /**
   * @param buffer buffer to convert
   * @return vint bytes as an integer.
   */
  public static long bytesToVint(final byte [] buffer) {
    int offset = 0;
    byte firstByte = buffer[offset++];
    int len = WritableUtils.decodeVIntSize(firstByte);
    if (len == 1) {
      return firstByte;
    }
    long i = 0;
    for (int idx = 0; idx < len-1; idx++) {
      byte b = buffer[offset++];
      i = i << 8;
      i = i | (b & 0xFF);
    }
    return (WritableUtils.isNegativeVInt(firstByte) ? ~i : i);
  }

  /**
   * Reads a zero-compressed encoded long from input stream and returns it.
   * @param buffer Binary array
   * @param offset Offset into array at which vint begins.
   * @throws java.io.IOException e
   * @return deserialized long from stream.
   */
  public static long readVLong(final byte [] buffer, final int offset)
  throws IOException {
    byte firstByte = buffer[offset];
    int len = WritableUtils.decodeVIntSize(firstByte);
    if (len == 1) {
      return firstByte;
    }
    long i = 0;
    for (int idx = 0; idx < len-1; idx++) {
      byte b = buffer[offset + 1 + idx];
      i = i << 8;
      i = i | (b & 0xFF);
    }
    return (WritableUtils.isNegativeVInt(firstByte) ? ~i : i);
  }

  /**
   * @param left left operand
   * @param right right operand
   * @return 0 if equal, < 0 if left is less than right, etc.
   */
  public static int compareTo(final byte [] left, final byte [] right) {
    return compareTo(left, 0, left.length, right, 0, right.length);
  }

  public static boolean useGuavaBytesComparision =
      HConstants.DEFAULT_USE_GUAVA_BYTES_COMPARISION;

  /**
   * Lexographically compare two arrays.
   *
   * @param buffer1 left operand
   * @param buffer2 right operand
   * @param offset1 Where to start comparing in the left buffer
   * @param offset2 Where to start comparing in the right buffer
   * @param length1 How much to compare from the left buffer
   * @param length2 How much to compare from the right buffer
   * @return 0 if equal, < 0 if left is less than right, etc.
   */
  public static int compareTo(byte[] buffer1, int offset1, int length1,
      byte[] buffer2, int offset2, int length2) {
    if (!useGuavaBytesComparision || (length1 < LONG_BYTES_X_2)
        || (length2 < LONG_BYTES_X_2)) {
      // Bring WritableComparator code local
      int end1 = offset1 + length1;
      int end2 = offset2 + length2;
      for (int i = offset1, j = offset2; i < end1 && j < end2; i++, j++) {
        int a = (buffer1[i] & 0xff);
        int b = (buffer2[j] & 0xff);
        if (a != b) {
          return a - b;
        }
      }
      return length1 - length2;
    } else {
      return LexicographicalComparerHolder.BEST_COMPARER.compareTo(
          buffer1, offset1, length1, buffer2, offset2, length2);
    }
  }

  /**
   * The number of bytes required to represent a primitive {@code long}
   * value.
   */
  public static final int LONG_BYTES = Long.SIZE / Byte.SIZE;
  public static final int LONG_BYTES_X_2 = LONG_BYTES * 2;

  interface Comparer<T> {
    abstract public int compareTo(T buffer1, int offset1, int length1,
        T buffer2, int offset2, int length2);
  }

  static Comparer<byte[]> lexicographicalComparerJavaImpl() {
    return LexicographicalComparerHolder.PureJavaComparer.INSTANCE;
  }

  /**
   * Provides a lexicographical comparer implementation; either a Java
   * implementation or a faster implementation based on {@link Unsafe}.
   *
   * <p>Uses reflection to gracefully fall back to the Java implementation if
   * {@code Unsafe} isn't available.
   */
  static class LexicographicalComparerHolder {
    static final String UNSAFE_COMPARER_NAME =
        LexicographicalComparerHolder.class.getName() + "$UnsafeComparer";

    static final Comparer<byte[]> BEST_COMPARER = getBestComparer();
    /**
     * Returns the Unsafe-using Comparer, or falls back to the pure-Java
     * implementation if unable to do so.
     */
    static Comparer<byte[]> getBestComparer() {
      try {
        Class<?> theClass = Class.forName(UNSAFE_COMPARER_NAME);

        // yes, UnsafeComparer does implement Comparer<byte[]>
        @SuppressWarnings("unchecked")
        Comparer<byte[]> comparer =
          (Comparer<byte[]>) theClass.getEnumConstants()[0];
        return comparer;
      } catch (Throwable t) { // ensure we really catch *everything*
        return lexicographicalComparerJavaImpl();
      }
    }

    enum PureJavaComparer implements Comparer<byte[]> {
      INSTANCE;

      @Override
      public int compareTo(byte[] buffer1, int offset1, int length1,
          byte[] buffer2, int offset2, int length2) {
        // Short circuit equal case
        if (buffer1 == buffer2 &&
            offset1 == offset2 &&
            length1 == length2) {
          return 0;
        }
        // Bring WritableComparator code local
        int end1 = offset1 + length1;
        int end2 = offset2 + length2;
        for (int i = offset1, j = offset2; i < end1 && j < end2; i++, j++) {
          int a = (buffer1[i] & 0xff);
          int b = (buffer2[j] & 0xff);
          if (a != b) {
            return a - b;
          }
        }
        return length1 - length2;
      }
    }

    enum UnsafeComparer implements Comparer<byte[]> {
      INSTANCE;

      static final Unsafe theUnsafe;

      /** The offset to the first element in a byte array. */
      static final int BYTE_ARRAY_BASE_OFFSET;

      static {
        theUnsafe = (Unsafe) AccessController.doPrivileged(
            new PrivilegedAction<Object>() {
              @Override
              public Object run() {
                try {
                  Field f = Unsafe.class.getDeclaredField("theUnsafe");
                  f.setAccessible(true);
                  return f.get(null);
                } catch (NoSuchFieldException e) {
                  // It doesn't matter what we throw;
                  // it's swallowed in getBestComparer().
                  throw new Error();
                } catch (IllegalAccessException e) {
                  throw new Error();
                }
              }
            });

        BYTE_ARRAY_BASE_OFFSET = theUnsafe.arrayBaseOffset(byte[].class);

        // sanity check - this should never fail
        if (theUnsafe.arrayIndexScale(byte[].class) != 1) {
          throw new AssertionError();
        }
      }

      static final boolean littleEndian =
        ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN);

      /**
       * Returns true if x1 is less than x2, when both values are treated as
       * unsigned.
       */
      static boolean lessThanUnsigned(long x1, long x2) {
        return (x1 + Long.MIN_VALUE) < (x2 + Long.MIN_VALUE);
      }

      /**
       * Lexicographically compare two arrays.
       *
       * @param buffer1 left operand
       * @param buffer2 right operand
       * @param offset1 Where to start comparing in the left buffer
       * @param offset2 Where to start comparing in the right buffer
       * @param length1 How much to compare from the left buffer
       * @param length2 How much to compare from the right buffer
       * @return 0 if equal, < 0 if left is less than right, etc.
       */
      @Override
      public int compareTo(byte[] buffer1, int offset1, int length1,
          byte[] buffer2, int offset2, int length2) {
        // Short circuit equal case
        if (buffer1 == buffer2 &&
            offset1 == offset2 &&
            length1 == length2) {
          return 0;
        }
        int minLength = Math.min(length1, length2);
        int minWords = minLength / LONG_BYTES;
        int offset1Adj = offset1 + BYTE_ARRAY_BASE_OFFSET;
        int offset2Adj = offset2 + BYTE_ARRAY_BASE_OFFSET;

        /*
         * Compare 8 bytes at a time. Benchmarking shows comparing 8 bytes at a
         * time is no slower than comparing 4 bytes at a time even on 32-bit.
         * On the other hand, it is substantially faster on 64-bit.
         */
        for (int i = 0; i < minWords * LONG_BYTES; i += LONG_BYTES) {
          long lw = theUnsafe.getLong(buffer1, offset1Adj + (long) i);
          long rw = theUnsafe.getLong(buffer2, offset2Adj + (long) i);
          long diff = lw ^ rw;

          if (diff != 0) {
            if (!littleEndian) {
              return lessThanUnsigned(lw, rw) ? -1 : 1;
            }

            // Use binary search
            int n = 0;
            int y;
            int x = (int) diff;
            if (x == 0) {
              x = (int) (diff >>> 32);
              n = 32;
            }

            y = x << 16;
            if (y == 0) {
              n += 16;
            } else {
              x = y;
            }

            y = x << 8;
            if (y == 0) {
              n += 8;
            }
            return (int) (((lw >>> n) & 0xFFL) - ((rw >>> n) & 0xFFL));
          }
        }

        // The epilogue to cover the last (minLength % 8) elements.
        for (int i = minWords * LONG_BYTES; i < minLength; i++) {
          int a = (buffer1[offset1 + i] & 0xff);
          int b = (buffer2[offset2 + i] & 0xff);
          if (a != b) {
            return a - b;
          }
        }
        return length1 - length2;
      }
    }
  }

  /**
   * @param left left operand
   * @param right right operand
   * @return True if equal
   */
  public static boolean equals(final byte [] left, final byte [] right) {
    // Could use Arrays.equals?
    //noinspection SimplifiableConditionalExpression
    if (left == null && right == null) {
      return true;
    }
    return (left == null || right == null || (left.length != right.length)
            ? false : compareTo(left, right) == 0);
  }

  public static boolean equals(final byte[] left, int leftOffset, int leftLength,
      final byte[] right, int rightOffset, int rightLength) {
    if (left == null && right == null) {
      return true;
    }
    return (left == null || right == null || (leftLength != rightLength) ? false : compareTo(left,
        leftOffset, leftLength, right, rightOffset, rightLength) == 0);
  }

  /**
   * Return true if the byte array on the right is a prefix of the byte
   * array on the left.
   */
  public static boolean startsWith(byte[] bytes, byte[] prefix) {
    return bytes != null && prefix != null &&
      bytes.length >= prefix.length &&
      compareTo(bytes, 0, prefix.length, prefix, 0, prefix.length) == 0;
  }

  /**
   * @param b bytes to hash
   * @return Runs {@link WritableComparator#hashBytes(byte[], int)} on the
   * passed in array.  This method is what {@link org.apache.hadoop.io.Text} and
   * {@link ImmutableBytesWritable} use calculating hash code.
   */
  public static int hashCode(final byte [] b) {
    return hashCode(b, b.length);
  }

  /**
   * @param b value
   * @param length length of the value
   * @return Runs {@link WritableComparator#hashBytes(byte[], int)} on the
   * passed in array.  This method is what {@link org.apache.hadoop.io.Text} and
   * {@link ImmutableBytesWritable} use calculating hash code.
   */
  public static int hashCode(final byte [] b, final int length) {
    return WritableComparator.hashBytes(b, length);
  }

  /**
   * @param b bytes to hash
   * @return A hash of <code>b</code> as an Integer that can be used as key in
   * Maps.
   */
  public static Integer mapKey(final byte [] b) {
    return hashCode(b);
  }

  /**
   * @param b bytes to hash
   * @param length length to hash
   * @return A hash of <code>b</code> as an Integer that can be used as key in
   * Maps.
   */
  public static Integer mapKey(final byte [] b, final int length) {
    return hashCode(b, length);
  }

  /**
   * @param a lower half
   * @param b upper half
   * @return New array that has a in lower half and b in upper half.
   */
  public static byte [] add(final byte [] a, final byte [] b) {
    return add(a, b, HConstants.EMPTY_BYTE_ARRAY);
  }

  /**
   * @param a first third
   * @param b second third
   * @param c third third
   * @return New array made from a, b and c
   */
  public static byte [] add(final byte [] a, final byte [] b, final byte [] c) {
    byte [] result = new byte[a.length + b.length + c.length];
    System.arraycopy(a, 0, result, 0, a.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    System.arraycopy(c, 0, result, a.length + b.length, c.length);
    return result;
  }

  /**
   * @param a first third
   * @param b second third
   * @param c third third
   * @return New array made from a, b and c
   */
  public static byte [] add(final byte [] a, int aOffset, int aLength,
      final byte [] b, int bOffset, int bLength,
      final byte [] c, int cOffset, int cLength) {
    byte [] result = new byte[aLength + bLength + cLength];
    System.arraycopy(a, aOffset, result, 0, aLength);
    System.arraycopy(b, bOffset, result, aLength, bLength);
    System.arraycopy(c, cOffset, result, aLength + bLength, cLength);
    return result;
  }

  /**
   * Returns the first elements of an array.
   *
   * NOTE the method may return <code>a</code> if possible.
   *
   * @param a
   *          a non-null array
   * @param length
   *          amount of bytes to grab
   * @return First <code>length</code> bytes from <code>a</code>
   */
  public static byte[] head(final byte[] a, final int length) {
    if (length > a.length) {
      throw new ArrayIndexOutOfBoundsException(length - 1);
    }
    if (length == a.length) {
      return a;
    }
    byte [] result = new byte[length];
    System.arraycopy(a, 0, result, 0, length);
    return result;
  }

  /**
   * Returns the last elements of an array.
   *
   * NOTE the method may return <code>a</code> if possible.
   *
   * @param a
   *          a non-null array
   * @param length
   *          amount of bytes to snarf
   * @return Last <code>length</code> bytes from <code>a</code>
   */
  public static byte[] tail(final byte[] a, final int length) {
    if (length > a.length) {
      throw new ArrayIndexOutOfBoundsException(a.length - length);
    }
    if (length == a.length) {
      return a;
    }
    byte [] result = new byte[length];
    System.arraycopy(a, a.length - length, result, 0, length);
    return result;
  }

  /**
   * Pads zeros in front of an array.
   *
   * NOTE the method may return <code>a</code> if possible.
   *
   * @param a
   *          a non-null array
   * @param length
   *          the number of zeros to be padded in
   * @return Value in <code>a</code> plus <code>length</code> prepended 0 bytes.
   *         could be the same instance of <code>a</code> if lenght == 0.
   */
  public static byte[] padHead(final byte[] a, final int length) {
    if (length == 0) {
      return a;
    }
    byte[] res = new byte[a.length + length];
    System.arraycopy(a, 0, res, length, a.length);
    return res;
  }

  /**
   * Appends zeros at the end of <code>a</code>.
   *
   * NOTE the method may return <code>a</code> if possible.
   *
   * @param a
   *          array
   * @param length
   *          new array size
   * @return Value in <code>a</code> plus <code>length</code> appended 0 bytes
   */
  public static byte [] padTail(final byte [] a, final int length) {
    return appendToTail(a, length, (byte)0);
  }

  /**
   * Appends length bytes to the end of the array and returns the new array
   * Fills byte b in the newly allocated space in the byte[].
   *
   * NOTE the method may return <code>a</code> if possible.
   *
   * @param a
   *          array
   * @param length
   *          new array size
   * @param b
   *          byte to write to the tail.
   * @return Value in <code>a</code> plus <code>length</code> appended 0 bytes
   */
  public static byte[] appendToTail(final byte[] a, int length, byte b) {
    if (length == 0) {
      return a;
    }
    int total = a.length + length;
    byte[] res = new byte[total];
    System.arraycopy(a, 0, res, 0, a.length);

    if (b != 0) {
      for (int i = a.length; i < total; i++) {
        res[i] = b;
      }
    }
    return res;
  }

  /**
   * Split passed range.  Expensive operation relatively.  Uses BigInteger math.
   * Useful splitting ranges for MapReduce jobs.
   * @param a Beginning of range
   * @param b End of range
   * @param num Number of times to split range.  Pass 1 if you want to split
   * the range in two; i.e. one split.
   * @return Array of dividing values
   */
  public static byte [][] split(final byte [] a, final byte [] b, final int num) {
    return split(a, b, false, num);
  }

  /**
   * Split passed range.  Expensive operation relatively.  Uses BigInteger math.
   * Useful splitting ranges for MapReduce jobs.
   * @param a Beginning of range
   * @param b End of range
   * @param inclusive Whether the end of range is prefix-inclusive or is
   * considered an exclusive boundary.  Automatic splits are generally exclusive
   * and manual splits with an explicit range utilize an inclusive end of range.
   * @param num Number of times to split range.  Pass 1 if you want to split
   * the range in two; i.e. one split.
   * @return Array of dividing values
   */
  public static byte[][] split(final byte[] a, final byte[] b,
      boolean inclusive, final int num) {
    byte[][] ret = new byte[num + 2][];
    int i = 0;
    Iterable<byte[]> iter = iterateOnSplits(a, b, inclusive, num);
    if (iter == null)
      return null;
    for (byte[] elem : iter) {
      ret[i++] = elem;
    }
    return ret;
  }

  /**
   * Iterate over keys within the passed range, splitting at an [a,b) boundary.
   */
  public static Iterable<byte[]> iterateOnSplits(final byte[] a,
      final byte[] b, final int num)
  {
    return iterateOnSplits(a, b, false, num);
  }

  /**
   * Iterate over keys within the passed range.
   */
  public static Iterable<byte[]> iterateOnSplits(
      final byte[] a, final byte[]b, boolean inclusive, final int num)
  {
    byte [] aPadded;
    byte [] bPadded;
    if (a.length < b.length) {
      aPadded = padTail(a, b.length - a.length);
      bPadded = b;
    } else if (b.length < a.length) {
      aPadded = a;
      bPadded = padTail(b, a.length - b.length);
    } else {
      aPadded = a;
      bPadded = b;
    }
    if (compareTo(aPadded,bPadded) >= 0) {
      throw new IllegalArgumentException("b <= a");
    }
    if (num < 0) {
      throw new IllegalArgumentException("num cannot be < 0");
    }
    byte [] prependHeader = {1, 0};
    final BigInteger startBI = new BigInteger(add(prependHeader, aPadded));
    final BigInteger stopBI = new BigInteger(add(prependHeader, bPadded));
    BigInteger diffBI = stopBI.subtract(startBI);
    if (inclusive) {
      diffBI = diffBI.add(BigInteger.ONE);
    }
    final BigInteger splitsBI = BigInteger.valueOf(num + 1);
    if(diffBI.compareTo(splitsBI) < 0) {
      return null;
    }
    final BigInteger intervalBI;
    try {
      intervalBI = diffBI.divide(splitsBI);
    } catch(Exception e) {
      LOG.error("Exception caught during division", e);
      return null;
    }

    final Iterator<byte[]> iterator = new Iterator<byte[]>() {
      private int i = -1;
      private BigInteger curBI = startBI;

      @Override
      public boolean hasNext() {
        return i < num+1;
      }

      @Override
      public byte[] next() {
        i++;
        if (i == 0) return a;
        if (i == num + 1) return b;

        curBI = curBI.add(intervalBI);
        byte [] padded = curBI.toByteArray();
        if (padded[1] == 0)
          padded = tail(padded, padded.length - 2);
        else
          padded = tail(padded, padded.length - 1);
        return padded;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }

    };

    return new Iterable<byte[]>() {
      @Override
      public Iterator<byte[]> iterator() {
        return iterator;
      }
    };
  }

  /**
   * Calculate the next <code>num</code> elements in arithemetic
   * progression sequence.
   *
   * @param a First element.
   * @param b Second element.
   * @param num Number of next elements to find.
   * @return <code>num</code> byte arrays each having the same interval
   *         from <code>a</code> to <code>b</code>, starting from b. In
   *         other words, it returns an array consists of b+(b-a)*(i+1),
   *         where i is the index of the resulting array of size <code>
   *         num</code>. Uses BigInteger math.
   */
  public static byte[][] arithmeticProgSeq(byte[] a, byte[] b, int num) {
    byte [][] result = new byte[num][];
    byte [] aPadded;
    byte [] bPadded;
    if (a.length < b.length) {
      aPadded = padTail(a, b.length - a.length);
      bPadded = b;
    } else if (b.length < a.length) {
      aPadded = a;
      bPadded = padTail(b, a.length - b.length);
    } else {
      aPadded = a;
      bPadded = b;
    }
    if (num < 0) {
      throw new IllegalArgumentException("num cannot be < 0");
    }
    byte [] prependHeader = {1, 0};
    BigInteger startBI = new BigInteger(add(prependHeader, aPadded));
    BigInteger stopBI = new BigInteger(add(prependHeader, bPadded));
    BigInteger diffBI = stopBI.subtract(startBI);
    BigInteger curBI = stopBI;
    for (int i = 0; i < num; i++) {
      curBI = curBI.add(diffBI);
      byte [] padded = curBI.toByteArray();
      if (padded[1] == 0)
        padded = tail(padded, padded.length - 2);
      else
        padded = tail(padded, padded.length - 1);
      result[i] = padded;
    }
    return result;
  }

  /**
   * @param t operands
   * @return Array of byte arrays made from passed array of Text
   */
  public static byte [][] toByteArrays(final String [] t) {
    byte [][] result = new byte[t.length][];
    for (int i = 0; i < t.length; i++) {
      result[i] = Bytes.toBytes(t[i]);
    }
    return result;
  }

  /**
   * @param column operand
   * @return A byte array of a byte array where first and only entry is
   * <code>column</code>
   */
  public static byte [][] toByteArrays(final String column) {
    return toByteArrays(toBytes(column));
  }

  /**
   * @param column operand
   * @return A byte array of a byte array where first and only entry is
   * <code>column</code>
   */
  public static byte [][] toByteArrays(final byte [] column) {
    byte [][] result = new byte[1][];
    result[0] = column;
    return result;
  }

  /**
   * Binary search for keys in indexes.
   *
   * @param arr array of byte arrays to search for
   * @param key the key you want to find
   * @param offset the offset in the key you want to find
   * @param length the length of the key
   * @param comparator a comparator to compare.
   * @return zero-based index of the key, if the key is present in the array.
   *         Otherwise, a value -(i + 1) such that the key is between arr[i -
   *         1] and arr[i] non-inclusively, where i is in [0, i], if we define
   *         arr[-1] = -Inf and arr[N] = Inf for an N-element array. The above
   *         means that this function can return 2N + 1 different values
   *         ranging from -(N + 1) to N - 1.
   */
  public static int binarySearch(byte [][]arr, byte []key, int offset,
      int length, RawComparator<byte []> comparator) {
    int low = 0;
    int high = arr.length - 1;

    while (low <= high) {
      int mid = (low+high) >>> 1;
      // we have to compare in this order, because the comparator order
      // has special logic when the 'left side' is a special key.
      int cmp = comparator.compare(key, offset, length,
          arr[mid], 0, arr[mid].length);
      // key lives above the midpoint
      if (cmp > 0)
        low = mid + 1;
      // key lives below the midpoint
      else if (cmp < 0)
        high = mid - 1;
      // BAM. how often does this really happen?
      else
        return mid;
    }
    return - (low+1);
  }

  /**
   * Bytewise binary increment/deincrement of long contained in byte array
   * on given amount.
   *
   * @param value - array of bytes containing long (length <= SIZEOF_LONG)
   * @param amount value will be incremented on (deincremented if negative)
   * @return array of bytes containing incremented long (length == SIZEOF_LONG)
   * @throws IOException - if value.length > SIZEOF_LONG
   */
  public static byte [] incrementBytes(byte[] value, long amount)
  throws IOException {
    byte[] val = value;
    if (val.length < SIZEOF_LONG) {
      // Hopefully this doesn't happen too often.
      byte [] newvalue;
      if (val[0] < 0) {
        newvalue = new byte[]{-1, -1, -1, -1, -1, -1, -1, -1};
      } else {
        newvalue = new byte[SIZEOF_LONG];
      }
      System.arraycopy(val, 0, newvalue, newvalue.length - val.length,
        val.length);
      val = newvalue;
    } else if (val.length > SIZEOF_LONG) {
      throw new IllegalArgumentException("Increment Bytes - value too big: " +
        val.length);
    }
    if(amount == 0) return val;
    if(val[0] < 0){
      return binaryIncrementNeg(val, amount);
    }
    return binaryIncrementPos(val, amount);
  }

  /* increment/deincrement for positive value */
  private static byte [] binaryIncrementPos(byte [] value, long amount) {
    long amo = amount;
    int sign = 1;
    if (amount < 0) {
      amo = -amount;
      sign = -1;
    }
    for(int i=0;i<value.length;i++) {
      int cur = ((int)amo % 256) * sign;
      amo = (amo >> 8);
      int val = value[value.length-i-1] & 0x0ff;
      int total = val + cur;
      if(total > 255) {
        amo += sign;
        total %= 256;
      } else if (total < 0) {
        amo -= sign;
      }
      value[value.length-i-1] = (byte)total;
      if (amo == 0) return value;
    }
    return value;
  }

  /* increment/deincrement for negative value */
  private static byte [] binaryIncrementNeg(byte [] value, long amount) {
    long amo = amount;
    int sign = 1;
    if (amount < 0) {
      amo = -amount;
      sign = -1;
    }
    for(int i=0;i<value.length;i++) {
      int cur = ((int)amo % 256) * sign;
      amo = (amo >> 8);
      int val = ((~value[value.length-i-1]) & 0x0ff) + 1;
      int total = cur - val;
      if(total >= 0) {
        amo += sign;
      } else if (total < -256) {
        amo -= sign;
        total %= 256;
      }
      value[value.length-i-1] = (byte)total;
      if (amo == 0) return value;
    }
    return value;
  }

  /**
   * Writes a string as a fixed-size field, padded with zeros.
   */
  public static void writeStringFixedSize(final DataOutput out, String s,
      int size) throws IOException {
    byte[] b = toBytes(s);
    if (b.length > size) {
      throw new IOException("Trying to write " + b.length + " bytes (" +
          toStringBinary(b) + ") into a field of length " + size);
    }

    out.writeBytes(s);
    for (int i = 0; i < size - s.length(); ++i)
      out.writeByte(0);
  }

  /**
   * Reads a fixed-size field and interprets it as a string padded with zeros.
   */
  public static String readStringFixedSize(final DataInput in, int size)
      throws IOException {
    byte[] b = new byte[size];
    in.readFully(b);
    int n = b.length;
    while (n > 0 && b[n - 1] == 0)
      --n;

    return toString(b, 0, n);
  }

  /**
   * @param b a byte buffer
   * @return true if the given byte buffer is non-null and non-empty (has remaining bytes)
   */
  public static boolean isNonEmpty(ByteBuffer b) {
    return b != null && b.remaining() > 0;
  }

  public static byte[] copyOfByteArray(byte[] arr) {
    byte[] tmp = new byte[arr.length];
    System.arraycopy(arr, 0, tmp, 0, arr.length);
    return tmp;
  }

  /**
   * Returns a byte array next to a given one, i.e. it is the smallest byte
   * array among all byte arrays that is strictly greater than the give array.
   * Greater and smaller are defined by Bytes.compareTo.
   *
   * @param b
   *          the give array
   */
  public static byte[] nextOf(byte[] b) {
    byte[] res = new byte[b.length + 1];
    System.arraycopy(b, 0, res, 0, b.length);
    return res;
  }

  /**
   * Return whether b equals nextOf(a)
   */
  public static boolean isNext(byte[] a, byte[] b) {
    if (a == null || b == null) {
      return false;
    }
    return isNext(a, 0, a.length, b, 0, b.length);
  }

  /**
   * Return whether b[...] equals nextOf(a[...])
   */
  public static boolean isNext(byte[] a, int aOffs, int aLen, byte[] b,
      int bOffs, int bLen) {
    if (a == null || b == null) {
      return false;
    }

    if (bLen != aLen + 1) {
      return false;
    }
    if (b[bOffs + aLen] != 0) {
      return false;
    }
    return Bytes.compareTo(a, aOffs, aLen, b, bOffs, aLen) == 0;
  }

  /**
   * This is a utility method, that serializes a Swift annotated class' object
   * into a byte array. This is equivalent to Writable.getBytes().
   *
   * @param t The object to be serialized.
   * @param clazz The class of the object to be serialized
   * @param <T>
   * @return The byte array corresponding to the serialized object.
   * @throws Exception
   */
  public static <T> byte[] writeThriftBytes(T t, Class<T> clazz)
    throws Exception {
    TMemoryBuffer buffer = writeThriftBytesAndGetBuffer(t, clazz);
    return (buffer.getArray().length == buffer.length()) ? buffer.getArray()
      : Arrays.copyOf(buffer.getArray(), buffer.length());
   }

  public static <T> String writeThriftBytesAndGetString(T t, Class<T> clazz)
    throws Exception {
    TMemoryBuffer buffer = writeThriftBytesAndGetBuffer(t, clazz);
    return bytesToString64(buffer.getArray(), 0, buffer.length());
  }

  /**
   * @param t
   * @param clazz
   * @return
   * @throws Exception
   */
  public static <T> TMemoryBuffer writeThriftBytesAndGetBuffer(T t,
    Class<T> clazz) throws Exception {
    ThriftCodec<T> codec =
      HBaseNiftyThriftServer.THRIFT_CODEC_MANAGER.getCodec(clazz);
    TMemoryBuffer buffer = new TMemoryBuffer(0);
    //TODO: adela change this to be configurable in future
    TProtocol protocol = new TFacebookCompactProtocol(buffer);
    codec.write(t, protocol);
    return buffer;
  }

  /**
   * This is a utility method, that deserializes a Swift annotated class' object
   * from a byte array. This is equivalent to Writable.getWritable().
   *
   * @param buff
   * @param clazz
   * @param <T>
   * @return
   * @throws Exception
   */
  public static <T> T readThriftBytes(byte[] buff, Class<T> clazz)
    throws Exception {
    ThriftCodec<T> codec =
      HBaseNiftyThriftServer.THRIFT_CODEC_MANAGER.getCodec(clazz);
    TMemoryInputTransport buffer = new TMemoryInputTransport(buff);
    // TODO: adela change this to be configurable in future
    TProtocol protocol = new TFacebookCompactProtocol(buffer);
    return codec.read(protocol);
  }

  public static int longestCommonPrefix(byte[] arr1, byte[] arr2) {
    int len = Math.min(arr1.length, arr2.length);
    for (int i = 0; i < len; i++) {
      if (arr1[i] != arr2[i]) return i;
    }
    return len;
  }

  /**
   * Converts a null byte[] into empty byte array, no change for other things.
   */
  public static byte[] nonNull(byte[] bytes) {
    if (bytes == null) {
      return HConstants.EMPTY_BYTE_ARRAY;
    }

    return bytes;
  }

  /**
   * Returns whether the range defined by the start and end rows is empty.
   *
   * @param start the start bound, inclusive. null or zero-length array means
   *          no bound.
   * @param end the end bound, exclusive. null or zero-length array means
   *          no bound.
   */
  public static boolean rangeNotEmpty(byte[] start, byte[] end) {
    if (start == null || start.length == 0) {
      return true;
    }

    if (end == null || end.length == 0) {
      return true;
    }

    return compareTo(start, end) < 0;
  }

  /**
   * Returns whether two ranges defined by start/end rows contain non-empty
   * overlap.
   */
  public static boolean rangesOverlapped(byte[] start1, byte[] end1,
      byte[] start2, byte[] end2) {
    Pair<byte[], byte[]> startEnd = rangeIntersect(start1, end1, start2, end2);

    return rangeNotEmpty(startEnd.getFirst(), startEnd.getSecond());
  }

  /**
   * Returns the intersect of two regions.
   *
   * @return Pair of start and end keys in order.
   */
  public static Pair<byte[], byte[]> rangeIntersect(byte[] start1,
      byte[] end1, byte[] start2, byte[] end2) {
    byte[] start = null;
    if (start1 == null || start1.length == 0) {
      start = start2;
    } else if (start2 == null || start2.length == 0) {
      start = start1;
    } else if (compareTo(start1, start2) > 0) {
      start = start1;
    } else {
      start = start2;
    }

    byte[] end = null;
    if (end1 == null || end1.length == 0) {
      end = end2;
    } else if (end2 == null || end2.length == 0) {
      end = end1;
    } else if (compareTo(end1, end2) < 0) {
      end = end1;
    } else {
      end = end2;
    }

    return new Pair<>(start, end);
  }
}
