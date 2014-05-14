/**
 * Copyright 2009 The Apache Software Foundation
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

import org.apache.hadoop.hbase.SmallTests;
import org.junit.Assert;
import org.junit.experimental.categories.Category;

@Category(SmallTests.class)
public class TestBytes extends TestCase {
  public void testNullHashCode() {
    byte [] b = null;
    Exception ee = null;
    try {
      Bytes.hashCode(b);
    } catch (Exception e) {
      ee = e;
    }
    assertNotNull(ee);
  }

  public void testSplit() throws Exception {
    byte [] lowest = Bytes.toBytes("AAA");
    byte [] middle = Bytes.toBytes("CCC");
    byte [] highest = Bytes.toBytes("EEE");
    byte [][] parts = Bytes.split(lowest, highest, 1);
    for (int i = 0; i < parts.length; i++) {
      System.out.println(Bytes.toString(parts[i]));
    }
    assertEquals(3, parts.length);
    assertTrue(Bytes.equals(parts[1], middle));
    // Now divide into three parts.  Change highest so split is even.
    highest = Bytes.toBytes("DDD");
    parts = Bytes.split(lowest, highest, 2);
    for (int i = 0; i < parts.length; i++) {
      System.out.println(Bytes.toString(parts[i]));
    }
    assertEquals(4, parts.length);
    // Assert that 3rd part is 'CCC'.
    assertTrue(Bytes.equals(parts[2], middle));
  }

  public void testSplit2() throws Exception {
    // More split tests.
    byte [] lowest = Bytes.toBytes("http://A");
    byte [] highest = Bytes.toBytes("http://z");
    byte [] middle = Bytes.toBytes("http://]");
    byte [][] parts = Bytes.split(lowest, highest, 1);
    for (int i = 0; i < parts.length; i++) {
      System.out.println(Bytes.toString(parts[i]));
    }
    assertEquals(3, parts.length);
    assertTrue(Bytes.equals(parts[1], middle));
  }

  public void testSplit3() throws Exception {
    // Test invalid split cases
    byte [] low = { 1, 1, 1 };
    byte [] high = { 1, 1, 3 };

    // If swapped, should throw IAE
    try {
      Bytes.split(high, low, 1);
      assertTrue("Should not be able to split if low > high", false);
    } catch(IllegalArgumentException iae) {
      // Correct
    }

    // Single split should work
    byte [][] parts = Bytes.split(low, high, 1);
    for (int i = 0; i < parts.length; i++) {
      System.out.println("" + i + " -> " + Bytes.toStringBinary(parts[i]));
    }
    assertTrue("Returned split should have 3 parts but has " + parts.length, parts.length == 3);

    // If split more than once, this should fail
    parts = Bytes.split(low, high, 2);
    assertTrue("Returned split but should have failed", parts == null);
  }

  public void testToLong() throws Exception {
    long [] longs = {-1l, 123l, 122232323232l};
    for (int i = 0; i < longs.length; i++) {
      byte [] b = Bytes.toBytes(longs[i]);
      assertEquals(longs[i], Bytes.toLong(b));
    }
  }

  public void testToFloat() throws Exception {
    float [] floats = {-1f, 123.123f, Float.MAX_VALUE};
    for (int i = 0; i < floats.length; i++) {
      byte [] b = Bytes.toBytes(floats[i]);
      assertEquals(floats[i], Bytes.toFloat(b));
    }
  }

  public void testToDouble() throws Exception {
    double [] doubles = {Double.MIN_VALUE, Double.MAX_VALUE};
    for (int i = 0; i < doubles.length; i++) {
      byte [] b = Bytes.toBytes(doubles[i]);
      assertEquals(doubles[i], Bytes.toDouble(b));
    }
  }

  public void testBinarySearch() throws Exception {
    byte [][] arr = {
        {1},
        {3},
        {5},
        {7},
        {9},
        {11},
        {13},
        {15},
    };
    byte [] key1 = {3,1};
    byte [] key2 = {4,9};
    byte [] key2_2 = {4};
    byte [] key3 = {5,11};
    byte [] key4 = {0};
    byte [] key5 = {2};

    assertEquals(1, Bytes.binarySearch(arr, key1, 0, 1,
      Bytes.BYTES_RAWCOMPARATOR));
    assertEquals(0, Bytes.binarySearch(arr, key1, 1, 1,
      Bytes.BYTES_RAWCOMPARATOR));
    assertEquals(-(2+1), Arrays.binarySearch(arr, key2_2,
      Bytes.BYTES_COMPARATOR));
    assertEquals(-(2+1), Bytes.binarySearch(arr, key2, 0, 1,
      Bytes.BYTES_RAWCOMPARATOR));
    assertEquals(4, Bytes.binarySearch(arr, key2, 1, 1,
      Bytes.BYTES_RAWCOMPARATOR));
    assertEquals(2, Bytes.binarySearch(arr, key3, 0, 1,
      Bytes.BYTES_RAWCOMPARATOR));
    assertEquals(5, Bytes.binarySearch(arr, key3, 1, 1,
      Bytes.BYTES_RAWCOMPARATOR));
    assertEquals(-1,
      Bytes.binarySearch(arr, key4, 0, 1, Bytes.BYTES_RAWCOMPARATOR));
    assertEquals(-2,
      Bytes.binarySearch(arr, key5, 0, 1, Bytes.BYTES_RAWCOMPARATOR));

    // Search for values to the left and to the right of each item in the array.
    for (int i = 0; i < arr.length; ++i) {
      assertEquals(-(i + 1), Bytes.binarySearch(arr,
          new byte[] { (byte) (arr[i][0] - 1) }, 0, 1,
          Bytes.BYTES_RAWCOMPARATOR));
      assertEquals(-(i + 2), Bytes.binarySearch(arr,
          new byte[] { (byte) (arr[i][0] + 1) }, 0, 1,
          Bytes.BYTES_RAWCOMPARATOR));
    }
  }

  public void testStartsWith() {
    assertTrue(Bytes.startsWith(Bytes.toBytes("hello"), Bytes.toBytes("h")));
    assertTrue(Bytes.startsWith(Bytes.toBytes("hello"), Bytes.toBytes("")));
    assertTrue(Bytes.startsWith(Bytes.toBytes("hello"), Bytes.toBytes("hello")));
    assertFalse(Bytes.startsWith(Bytes.toBytes("hello"), Bytes.toBytes("helloworld")));
    assertFalse(Bytes.startsWith(Bytes.toBytes(""), Bytes.toBytes("hello")));
  }

  public void testIncrementBytes() throws IOException {

    assertTrue(checkTestIncrementBytes(10, 1));
    assertTrue(checkTestIncrementBytes(12, 123435445));
    assertTrue(checkTestIncrementBytes(124634654, 1));
    assertTrue(checkTestIncrementBytes(10005460, 5005645));
    assertTrue(checkTestIncrementBytes(1, -1));
    assertTrue(checkTestIncrementBytes(10, -1));
    assertTrue(checkTestIncrementBytes(10, -5));
    assertTrue(checkTestIncrementBytes(1005435000, -5));
    assertTrue(checkTestIncrementBytes(10, -43657655));
    assertTrue(checkTestIncrementBytes(-1, 1));
    assertTrue(checkTestIncrementBytes(-26, 5034520));
    assertTrue(checkTestIncrementBytes(-10657200, 5));
    assertTrue(checkTestIncrementBytes(-12343250, 45376475));
    assertTrue(checkTestIncrementBytes(-10, -5));
    assertTrue(checkTestIncrementBytes(-12343250, -5));
    assertTrue(checkTestIncrementBytes(-12, -34565445));
    assertTrue(checkTestIncrementBytes(-1546543452, -34565445));
  }

  private static boolean checkTestIncrementBytes(long val, long amount)
  throws IOException {
    byte[] value = Bytes.toBytes(val);
    byte [] testValue = {-1, -1, -1, -1, -1, -1, -1, -1};
    if (value[0] > 0) {
      testValue = new byte[Bytes.SIZEOF_LONG];
    }
    System.arraycopy(value, 0, testValue, testValue.length - value.length,
        value.length);

    long incrementResult = Bytes.toLong(Bytes.incrementBytes(value, amount));

    return (Bytes.toLong(testValue) + amount) == incrementResult;
  }

  public void testFixedSizeString() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    Bytes.writeStringFixedSize(dos, "Hello", 5);
    Bytes.writeStringFixedSize(dos, "World", 18);
    Bytes.writeStringFixedSize(dos, "", 9);

    try {
      // Use a long dash which is three bytes in UTF-8. If encoding happens
      // using ISO-8859-1, this will fail.
      Bytes.writeStringFixedSize(dos, "Too\u2013Long", 9);
      fail("Exception expected");
    } catch (IOException ex) {
      assertEquals(
          "Trying to write 10 bytes (Too\\xE2\\x80\\x93Long) into a field of " +
          "length 9", ex.getMessage());
    }

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    DataInputStream dis = new DataInputStream(bais);
    assertEquals("Hello", Bytes.readStringFixedSize(dis, 5));
    assertEquals("World", Bytes.readStringFixedSize(dis, 18));
    assertEquals("", Bytes.readStringFixedSize(dis, 9));
  }

  public void testHead() throws Exception {
    byte[] a = new byte[] { 1, 2, 3, 4 };
    Assert.assertArrayEquals("head(a, 0)", Bytes.head(a, 0), new byte[] {});
    Assert.assertArrayEquals("head(a, 2)", Bytes.head(a, 2),
        new byte[] { 1, 2 });
    Assert.assertArrayEquals("head(a, 4)", Bytes.head(a, 4), new byte[] { 1, 2,
        3, 4 });
    try {
      Bytes.head(a, 5);
      fail("Should throw exception for head(a, 5)");
    } catch (ArrayIndexOutOfBoundsException e) {
      // Correct
    }
  }

  public void testTail() throws Exception {
    byte[] a = new byte[] { 1, 2, 3, 4 };
    Assert.assertArrayEquals("tail(a, 0)", Bytes.tail(a, 0), new byte[] {});
    Assert.assertArrayEquals("tail(a, 2)", Bytes.tail(a, 2),
        new byte[] { 3, 4 });
    Assert.assertArrayEquals("tail(a, 4)", Bytes.tail(a, 4), new byte[] { 1, 2,
        3, 4 });
    try {
      Bytes.tail(a, 5);
      fail("Should throw exception for tail(a, 5)");
    } catch (ArrayIndexOutOfBoundsException e) {
      // Correct
    }
  }

  public void testPadHead() throws Exception {
    byte[] a = new byte[] { 1, 2 };
    Assert.assertArrayEquals("padHead(a, 0)", Bytes.padHead(a, 0), new byte[] {
        1, 2 });
    Assert.assertArrayEquals("padHead(a, 2)", Bytes.padHead(a, 2), new byte[] {
        0, 0, 1, 2 });
  }

  public void testAppendToTail() throws Exception {
    byte[] a = new byte[] { 1, 2 };
    Assert.assertArrayEquals("appendToTail(a, 0, 0)",
        Bytes.appendToTail(a, 0, (byte) 0), new byte[] { 1, 2 });
    Assert.assertArrayEquals("appendToTail(a, 2, 0)",
        Bytes.appendToTail(a, 2, (byte) 0), new byte[] { 1, 2, 0, 0 });
    Assert.assertArrayEquals("appendToTail(a, 0, 6)",
        Bytes.appendToTail(a, 2, (byte) 6), new byte[] { 1, 2, 6, 6 });
  }

  /**
   * The rows in a given region follow the following pattern:
   * [PREFIX BYTES][ID BYTES]
   *
   * @param numRows : number of rows to return in the list.
   * @param prefixLength : length of common prefix.
   * @param length : length of the rest of the row.
   * @return : List of rows generated
   */
  private List<byte[]> getRowsRandom(int numRows, int prefixLength, int length) {
    Random r = new Random();
    byte[] prefixBytes = new byte[prefixLength];
    r.nextBytes(prefixBytes);
    List<byte[]> list = new ArrayList<byte[]>();
    for (int i=0; i<numRows; i++) {
      byte[] b = new byte[length];
      r.nextBytes(b);
      byte[] finalBytes = new byte[prefixLength + length];
      Bytes.putBytes(finalBytes, 0, prefixBytes, 0, prefixLength);
      Bytes.putBytes(finalBytes, prefixLength, b, 0, b.length);
      list.add(finalBytes);
    }
    return list;
  }

  public void testComparator() {
    // The rows in a given region follow the following pattern:
    // [PREFIX BYTES][ID BYTES]
    // With long prefixes, the comparison using Guava is faster.
    // With fewer common bytes, the Guava comparison is slower.
    for (int PREFIX = 50; PREFIX >= 0; PREFIX -= 10) {
      int ID = 100;
      int numRows = 1000;
      List<byte[]> list = getRowsRandom(numRows, PREFIX, ID);

      // Correctness
      for (int i=0; i<numRows; i++) {
        for (int j=0; j<numRows; j++) {
          Bytes.useGuavaBytesComparision = true;
          int bg = Bytes.compareTo(list.get(i), list.get(j));
          Bytes.useGuavaBytesComparision = false;
          int bs = Bytes.compareTo(list.get(i), list.get(j));
          assertTrue(bg + " != " + bs, bg == bs);
        }
      }
    }
  }

  public void testNonNull() throws Exception {
    Assert.assertArrayEquals("nonNull(null)", new byte[0], Bytes.nonNull(null));
    Assert.assertArrayEquals("nonNull([])", new byte[0],
        Bytes.nonNull(new byte[0]));
    Assert.assertArrayEquals("nonNull([1])", new byte[] { 1 },
        Bytes.nonNull(new byte[] { 1 }));
  }
}
