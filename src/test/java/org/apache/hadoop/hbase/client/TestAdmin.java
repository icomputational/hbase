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
package org.apache.hadoop.hbase.client;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HServerAddress;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.MediumTests;
import org.apache.hadoop.hbase.TableExistsException;
import org.apache.hadoop.hbase.TableNotDisabledException;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.UnstableTests;
import org.apache.hadoop.hbase.master.HMaster;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Class to test HBaseAdmin.
 * Spins up the minicluster once at test start and then takes it down afterward.
 * Add any testing of HBaseAdmin functionality here.
 */
@Category(MediumTests.class)
public class TestAdmin {
  final Log LOG = LogFactory.getLog(getClass());
  private final static HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private HBaseAdmin admin;
  private static final int NUM_REGION_SERVER = 3;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    TEST_UTIL.getConfiguration().setInt("hbase.regionserver.msginterval", 100);
    TEST_UTIL.getConfiguration().setInt("hbase.client.pause", 250);
    TEST_UTIL.getConfiguration().setInt("hbase.client.retries.number", 6);
    TEST_UTIL.startMiniCluster(NUM_REGION_SERVER);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before
  public void setUp() throws Exception {
    this.admin = new HBaseAdmin(TEST_UTIL.getConfiguration());
  }

  @Test
  public void testCreateTable() throws IOException {
    HTableDescriptor [] tables = admin.listTables();
    int numTables = tables.length;
    TEST_UTIL.createTable(Bytes.toBytes("testCreateTable"),
      HConstants.CATALOG_FAMILY);
    tables = this.admin.listTables();
    assertEquals(numTables + 1, tables.length);
  }

  @Test
  public void testGetTableRegions() throws IOException {
    TEST_UTIL.createTable(Bytes.toBytes("testCreateNormalTable"),
      HConstants.CATALOG_FAMILY);
    HMaster master = TEST_UTIL.getMiniHBaseCluster().getMaster();
    List<Pair<HRegionInfo,HServerAddress>>  tableRegions = 
        master.getTableRegions(Bytes.toBytes("testCreateTable"));
    assertTrue(tableRegions.size() != 0);
    List<Pair<HRegionInfo,HServerAddress>>  metaRegions = 
        master.getTableRegions(HConstants.META_TABLE_NAME);
    assertTrue(metaRegions.size() != 0);
  }


  @Test
  public void testCreateTableWithRegions() throws IOException {

    byte[] tableName = Bytes.toBytes("testCreateTableWithRegions");

    byte [][] splitKeys = {
        new byte [] { 1, 1, 1 },
        new byte [] { 2, 2, 2 },
        new byte [] { 3, 3, 3 },
        new byte [] { 4, 4, 4 },
        new byte [] { 5, 5, 5 },
        new byte [] { 6, 6, 6 },
        new byte [] { 7, 7, 7 },
        new byte [] { 8, 8, 8 },
        new byte [] { 9, 9, 9 },
    };
    int expectedRegions = splitKeys.length + 1;

    HTableDescriptor desc = new HTableDescriptor(tableName);
    desc.addFamily(new HColumnDescriptor(HConstants.CATALOG_FAMILY));
    admin.createTable(desc, splitKeys);

    HTable ht = new HTable(TEST_UTIL.getConfiguration(), tableName);
    Map<HRegionInfo,HServerAddress> regions = ht.getRegionsInfo();
    assertEquals("Tried to create " + expectedRegions + " regions " +
        "but only found " + regions.size(),
        expectedRegions, regions.size());
    System.err.println("Found " + regions.size() + " regions");

    Iterator<HRegionInfo> hris = regions.keySet().iterator();
    HRegionInfo hri = hris.next();
    assertTrue(hri.getStartKey() == null || hri.getStartKey().length == 0);
    assertTrue(Bytes.equals(hri.getEndKey(), splitKeys[0]));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), splitKeys[0]));
    assertTrue(Bytes.equals(hri.getEndKey(), splitKeys[1]));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), splitKeys[1]));
    assertTrue(Bytes.equals(hri.getEndKey(), splitKeys[2]));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), splitKeys[2]));
    assertTrue(Bytes.equals(hri.getEndKey(), splitKeys[3]));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), splitKeys[3]));
    assertTrue(Bytes.equals(hri.getEndKey(), splitKeys[4]));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), splitKeys[4]));
    assertTrue(Bytes.equals(hri.getEndKey(), splitKeys[5]));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), splitKeys[5]));
    assertTrue(Bytes.equals(hri.getEndKey(), splitKeys[6]));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), splitKeys[6]));
    assertTrue(Bytes.equals(hri.getEndKey(), splitKeys[7]));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), splitKeys[7]));
    assertTrue(Bytes.equals(hri.getEndKey(), splitKeys[8]));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), splitKeys[8]));
    assertTrue(hri.getEndKey() == null || hri.getEndKey().length == 0);

    // Now test using start/end with a number of regions

    // Use 80 bit numbers to make sure we aren't limited
    byte [] startKey = { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 };
    byte [] endKey =   { 9, 9, 9, 9, 9, 9, 9, 9, 9, 9 };

    // Splitting into 10 regions, we expect (null,1) ... (9, null)
    // with (1,2) (2,3) (3,4) (4,5) (5,6) (6,7) (7,8) (8,9) in the middle

    expectedRegions = 10;

    byte [] TABLE_2 = Bytes.add(tableName, Bytes.toBytes("_2"));

    desc = new HTableDescriptor(TABLE_2);
    desc.addFamily(new HColumnDescriptor(HConstants.CATALOG_FAMILY));
    admin = new HBaseAdmin(TEST_UTIL.getConfiguration());
    admin.createTable(desc, startKey, endKey, expectedRegions);

    ht = new HTable(TEST_UTIL.getConfiguration(), TABLE_2);
    regions = ht.getRegionsInfo();
    assertEquals("Tried to create " + expectedRegions + " regions " +
        "but only found " + regions.size(),
        expectedRegions, regions.size());
    System.err.println("Found " + regions.size() + " regions");

    hris = regions.keySet().iterator();
    hri = hris.next();
    assertTrue(hri.getStartKey() == null || hri.getStartKey().length == 0);
    assertTrue(Bytes.equals(hri.getEndKey(), new byte [] {1,1,1,1,1,1,1,1,1,1}));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), new byte [] {1,1,1,1,1,1,1,1,1,1}));
    assertTrue(Bytes.equals(hri.getEndKey(), new byte [] {2,2,2,2,2,2,2,2,2,2}));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), new byte [] {2,2,2,2,2,2,2,2,2,2}));
    assertTrue(Bytes.equals(hri.getEndKey(), new byte [] {3,3,3,3,3,3,3,3,3,3}));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), new byte [] {3,3,3,3,3,3,3,3,3,3}));
    assertTrue(Bytes.equals(hri.getEndKey(), new byte [] {4,4,4,4,4,4,4,4,4,4}));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), new byte [] {4,4,4,4,4,4,4,4,4,4}));
    assertTrue(Bytes.equals(hri.getEndKey(), new byte [] {5,5,5,5,5,5,5,5,5,5}));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), new byte [] {5,5,5,5,5,5,5,5,5,5}));
    assertTrue(Bytes.equals(hri.getEndKey(), new byte [] {6,6,6,6,6,6,6,6,6,6}));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), new byte [] {6,6,6,6,6,6,6,6,6,6}));
    assertTrue(Bytes.equals(hri.getEndKey(), new byte [] {7,7,7,7,7,7,7,7,7,7}));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), new byte [] {7,7,7,7,7,7,7,7,7,7}));
    assertTrue(Bytes.equals(hri.getEndKey(), new byte [] {8,8,8,8,8,8,8,8,8,8}));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), new byte [] {8,8,8,8,8,8,8,8,8,8}));
    assertTrue(Bytes.equals(hri.getEndKey(), new byte [] {9,9,9,9,9,9,9,9,9,9}));
    hri = hris.next();
    assertTrue(Bytes.equals(hri.getStartKey(), new byte [] {9,9,9,9,9,9,9,9,9,9}));
    assertTrue(hri.getEndKey() == null || hri.getEndKey().length == 0);

    // Try once more with something that divides into something infinite

    startKey = new byte [] { 0, 0, 0, 0, 0, 0 };
    endKey = new byte [] { 1, 0, 0, 0, 0, 0 };

    expectedRegions = 5;

    byte [] TABLE_3 = Bytes.add(tableName, Bytes.toBytes("_3"));

    desc = new HTableDescriptor(TABLE_3);
    desc.addFamily(new HColumnDescriptor(HConstants.CATALOG_FAMILY));
    admin = new HBaseAdmin(TEST_UTIL.getConfiguration());
    admin.createTable(desc, startKey, endKey, expectedRegions);

    ht = new HTable(TEST_UTIL.getConfiguration(), TABLE_3);
    regions = ht.getRegionsInfo();
    assertEquals("Tried to create " + expectedRegions + " regions " +
        "but only found " + regions.size(),
        expectedRegions, regions.size());
    System.err.println("Found " + regions.size() + " regions");

    // Try an invalid case where there are duplicate split keys
    splitKeys = new byte [][] {
        new byte [] { 1, 1, 1 },
        new byte [] { 2, 2, 2 },
        new byte [] { 3, 3, 3 },
        new byte [] { 2, 2, 2 }
    };

    byte [] TABLE_4 = Bytes.add(tableName, Bytes.toBytes("_4"));
    desc = new HTableDescriptor(TABLE_4);
    desc.addFamily(new HColumnDescriptor(HConstants.CATALOG_FAMILY));
    admin = new HBaseAdmin(TEST_UTIL.getConfiguration());
    try {
      admin.createTable(desc, splitKeys);
      assertTrue("Should not be able to create this table because of " +
          "duplicate split keys", false);
    } catch(IllegalArgumentException iae) {
      // Expected
    }
  }

  @Test
  public void testDisableAndEnableTable() throws IOException {
    final byte [] row = Bytes.toBytes("row");
    final byte [] qualifier = Bytes.toBytes("qualifier");
    final byte [] value = Bytes.toBytes("value");
    final byte [] table = Bytes.toBytes("testDisableAndEnableTable");
    HTable ht = TEST_UTIL.createTable(table, HConstants.CATALOG_FAMILY);
    
    Put put = new Put(row);
    put.add(HConstants.CATALOG_FAMILY, qualifier, value);
    ht.put(put);
    
    this.admin.disableTable(table);
    
    // Test that table is disabled
    Get get = new Get(row);
    get.addColumn(HConstants.CATALOG_FAMILY, qualifier);
    
    boolean ok = false;
    try {
      ht.get(get);
    } catch (RetriesExhaustedException e) {
      ok = true;
    }
    
    // with online schema change it is possible to add column 
    // without disabling the table
    assertEquals(true, ok);
    this.admin.enableTable(table);
    ok = true; 
    //Test that table is enabled
    try {
      ht.get(get);
    } catch (RetriesExhaustedException e) {
      ok = false;
    }
    assertEquals(true, ok);
  }

  @Test
  public void testTableExist() throws IOException {
    final byte [] table = Bytes.toBytes("testTableExist");
    boolean exist = false;
    exist = this.admin.tableExists(table);
    assertEquals(false, exist);
    TEST_UTIL.createTable(table, HConstants.CATALOG_FAMILY);
    exist = this.admin.tableExists(table);
    assertEquals(true, exist);
  }

  /**
   * Tests forcing split from client and having scanners successfully ride over split.
   * @throws Exception
   * @throws IOException
   */
  @Test
  public void testForceSplit() throws Exception {
    splitTest(null);
    splitTest(Bytes.toBytes("pwn"));
  }
  
  void splitTest(byte[] splitPoint) throws Exception {
    byte [] familyName = HConstants.CATALOG_FAMILY;
    byte [] tableName = Bytes.toBytes("testForceSplit");
    final HTable table = TEST_UTIL.createTable(tableName, familyName);
    try {
      byte[] k = new byte[3];
      int rowCount = 0;
      for (byte b1 = 'a'; b1 < 'z'; b1++) {
        for (byte b2 = 'a'; b2 < 'z'; b2++) {
          for (byte b3 = 'a'; b3 < 'z'; b3++) {
            k[0] = b1;
            k[1] = b2;
            k[2] = b3;
            Put put = new Put(k);
            put.add(familyName, new byte[0], k);
            table.put(put);
            rowCount++;
          }
        }
      }
  
      // get the initial layout (should just be one region)
      Map<HRegionInfo,HServerAddress> m = table.getRegionsInfo();
      System.out.println("Initial regions (" + m.size() + "): " + m);
      assertTrue(m.size() == 1);
  
      // Verify row count
      Scan scan = new Scan();
      ResultScanner scanner = table.getScanner(scan);
      int rows = 0;
      for(@SuppressWarnings("unused") Result result : scanner) {
        rows++;
      }
      scanner.close();
      assertEquals(rowCount, rows);
  
      // Have an outstanding scan going on to make sure we can scan over splits.
      scan = new Scan();
      scanner = table.getScanner(scan);
      // Scan first row so we are into first region before split happens.
      scanner.next();
  
      final AtomicInteger count = new AtomicInteger(0);
      Thread t = new Thread("CheckForSplit") {
        public void run() {
          for (int i = 0; i < 20; i++) {
            try {
              sleep(1000);
            } catch (InterruptedException e) {
              continue;
            }
            // check again    table = new HTable(conf, tableName);
            Map<HRegionInfo, HServerAddress> regions = null;
            try {
              regions = table.getRegionsInfo();
            } catch (IOException e) {
              e.printStackTrace();
            }
            if (regions == null) continue;
            count.set(regions.size());
            if (count.get() >= 2) break;
            LOG.debug("Cycle waiting on split");
          }
        }
      };
      t.start();
      // tell the master to split the table
      if (splitPoint != null) {
        admin.split(tableName, splitPoint);
      } else {
        admin.split(tableName);
      }
      t.join();
  
      // Verify row count
      rows = 1; // We counted one row above.
      for (@SuppressWarnings("unused") Result result : scanner) {
        rows++;
        if (rows > rowCount) {
          scanner.close();
          assertTrue("Scanned more than expected (" + rowCount + ")", false);
        }
      }
      scanner.close();
      assertEquals(rowCount, rows);
      
      if (splitPoint != null) {
        // make sure the split point matches our explicit configuration
        Map<HRegionInfo, HServerAddress> regions = null;
        try {
          regions = table.getRegionsInfo();
        } catch (IOException e) {
          e.printStackTrace();
        }
        assertEquals(2, regions.size());
        HRegionInfo[] r = regions.keySet().toArray(new HRegionInfo[0]);
        assertEquals(Bytes.toString(splitPoint), 
            Bytes.toString(r[0].getEndKey()));
        assertEquals(Bytes.toString(splitPoint), 
            Bytes.toString(r[1].getStartKey()));
        LOG.debug("Properly split on " + Bytes.toString(splitPoint));
      }
    } finally {
      TEST_UTIL.deleteTable(tableName);
    }
  }

  /**
   * HADOOP-2156
   * @throws IOException
   */
  @Test (expected=IllegalArgumentException.class)
  public void testEmptyHHTableDescriptor() throws IOException {
    this.admin.createTable(new HTableDescriptor());
  }

  @Test
  public void testEnableDisableAddColumnDeleteColumn() throws Exception {
    byte [] tableName = Bytes.toBytes("testMasterAdmin");
    TEST_UTIL.createTable(tableName, HConstants.CATALOG_FAMILY);
    this.admin.disableTable(tableName);
    try {
      new HTable(TEST_UTIL.getConfiguration(), tableName);
    } catch (org.apache.hadoop.hbase.client.RegionOfflineException e) {
      // Expected
    }
    this.admin.addColumn(tableName, new HColumnDescriptor("col2"));
    this.admin.enableTable(tableName);
    this.admin.disableTable(tableName);
    this.admin.deleteColumn(tableName, Bytes.toBytes("col2"));
    this.admin.deleteTable(tableName);
  }

  @Test
  public void testCreateBadTables() throws IOException {
    String msg = null;
    try {
      if (HTableDescriptor.isMetaregionSeqidRecordEnabled(TEST_UTIL.getConfiguration())) {
        this.admin.createTable(HTableDescriptor.ROOT_TABLEDESC_WITH_HISTORIAN_COLUMN);
      } else {
        this.admin.createTable(HTableDescriptor.ROOT_TABLEDESC);
      }
    } catch (IllegalArgumentException e) {
      msg = e.toString();
    }
    assertTrue("Unexcepted exception message " + msg, msg != null &&
      msg.startsWith(IllegalArgumentException.class.getName()) &&
      msg.contains(HTableDescriptor.ROOT_TABLEDESC.getNameAsString()));
    msg = null;
    try {
      this.admin.createTable(HTableDescriptor.META_TABLEDESC);
    } catch(IllegalArgumentException e) {
      msg = e.toString();
    }
    assertTrue("Unexcepted exception message " + msg, msg != null &&
      msg.startsWith(IllegalArgumentException.class.getName()) &&
      msg.contains(HTableDescriptor.META_TABLEDESC.getNameAsString()));

    // Now try and do concurrent creation with a bunch of threads.
    final HTableDescriptor threadDesc =
      new HTableDescriptor("threaded_testCreateBadTables");
    threadDesc.addFamily(new HColumnDescriptor(HConstants.CATALOG_FAMILY));
    int count = 10;
    Thread [] threads = new Thread [count];
    final AtomicInteger successes = new AtomicInteger(0);
    final AtomicInteger failures = new AtomicInteger(0);
    final HBaseAdmin localAdmin = this.admin;
    for (int i = 0; i < count; i++) {
      threads[i] = new Thread(Integer.toString(i)) {
        @Override
        public void run() {
          try {
            localAdmin.createTable(threadDesc);
            successes.incrementAndGet();
          } catch (TableExistsException e) {
            failures.incrementAndGet();
          } catch (IOException e) {
            throw new RuntimeException("Failed threaded create" + getName(), e);
          }
        }
      };
    }
    for (int i = 0; i < count; i++) {
      threads[i].start();
    }
    for (int i = 0; i < count; i++) {
      while(threads[i].isAlive()) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          // continue
        }
      }
    }
    // All threads are now dead.  Count up how many tables were created and
    // how many failed w/ appropriate exception.
    assertEquals(1, successes.get());
    assertEquals(count - 1, failures.get());
  }

  /**
   * Test for hadoop-1581 'HBASE: Unopenable tablename bug'.
   * @throws Exception
   */
  @Test
  public void testTableNameClash() throws Exception {
    String name = "testTableNameClash";
    admin.createTable(new HTableDescriptor(name + "SOMEUPPERCASE"));
    admin.createTable(new HTableDescriptor(name));
    // Before fix, below would fail throwing a NoServerForRegionException.
    new HTable(TEST_UTIL.getConfiguration(), name);
  }

  /**
   * Test read only tables
   * @throws Exception
   */
  @Test
  public void testReadOnlyTable() throws Exception {
    byte [] name = Bytes.toBytes("testReadOnlyTable");
    HTable table = TEST_UTIL.createTable(name, HConstants.CATALOG_FAMILY);
    byte[] value = Bytes.toBytes("somedata");
    // This used to use an empty row... That must have been a bug
    Put put = new Put(value);
    put.add(HConstants.CATALOG_FAMILY, HConstants.CATALOG_FAMILY, value);
    table.put(put);
  }

  /**
   * Test that user table names can contain '-' and '.' so long as they do not
   * start with same. HBASE-771
   * @throws IOException
   */
  @Test
  public void testTableNames() throws IOException {
    byte[][] illegalNames = new byte[][] {
        Bytes.toBytes("-bad"),
        Bytes.toBytes(".bad"),
        HConstants.ROOT_TABLE_NAME,
        HConstants.META_TABLE_NAME
    };
    for (int i = 0; i < illegalNames.length; i++) {
      try {
        new HTableDescriptor(illegalNames[i]);
        throw new IOException("Did not detect '" +
          Bytes.toString(illegalNames[i]) + "' as an illegal user table name");
      } catch (IllegalArgumentException e) {
        // expected
      }
    }
    byte[] legalName = Bytes.toBytes("g-oo.d");
    try {
      new HTableDescriptor(legalName);
    } catch (IllegalArgumentException e) {
      throw new IOException("Legal user table name: '" +
        Bytes.toString(legalName) + "' caused IllegalArgumentException: " +
        e.getMessage());
    }
  }

  /**
   * For HADOOP-2579
   * @throws IOException
   */
  @Test (expected=TableExistsException.class)
  public void testTableNotFoundExceptionWithATable() throws IOException {
    final byte [] name = Bytes.toBytes("testTableNotFoundExceptionWithATable");
    TEST_UTIL.createTable(name, HConstants.CATALOG_FAMILY);
    TEST_UTIL.createTable(name, HConstants.CATALOG_FAMILY);
  }

  /**
   * For HADOOP-2579
   * @throws IOException
   */
  @Test (expected=TableNotFoundException.class)
  public void testTableNotFoundExceptionWithoutAnyTables() throws IOException {
    new HTable(TEST_UTIL.getConfiguration(),
        "testTableNotFoundExceptionWithoutAnyTables");
  }

  @Test(timeout = 300000)
  public void testHundredsOfTable() throws IOException{
    final int times = 100;
    HColumnDescriptor fam1 = new HColumnDescriptor("fam1");
    HColumnDescriptor fam2 = new HColumnDescriptor("fam2");
    HColumnDescriptor fam3 = new HColumnDescriptor("fam3");

    for(int i = 0; i < times; i++) {
      HTableDescriptor htd = new HTableDescriptor("table"+i);
      htd.addFamily(fam1);
      htd.addFamily(fam2);
      htd.addFamily(fam3);
      this.admin.createTable(htd);
    }

    for(int i = 0; i < times; i++) {
      String tableName = "table"+i;
      this.admin.disableTable(tableName);
      this.admin.enableTable(tableName);
      this.admin.disableTable(tableName);
      this.admin.deleteTable(tableName);
    }
  }

  @Test
  public void testGetTableDescriptor() throws IOException {
    HColumnDescriptor fam1 = new HColumnDescriptor("fam1");
    HColumnDescriptor fam2 = new HColumnDescriptor("fam2");
    HColumnDescriptor fam3 = new HColumnDescriptor("fam3");
    HTableDescriptor htd = new HTableDescriptor("myTestTable");
    htd.addFamily(fam1);
    htd.addFamily(fam2);
    htd.addFamily(fam3);
    this.admin.createTable(htd);
    HTable table = new HTable(TEST_UTIL.getConfiguration(), "myTestTable");
    HTableDescriptor confirmedHtd = table.getTableDescriptor();

    assertEquals(htd.compareTo(confirmedHtd), 0);
  }
  
  @Test
  public void testOnlineChangeTableSchema() throws IOException,
      InterruptedException {
    final byte[] tableName = Bytes.toBytes("changeTableSchemaOnline");
    HTableDescriptor[] tables = admin.listTables();
    int numTables = tables.length;
    TEST_UTIL.createTable(tableName, HConstants.CATALOG_FAMILY);
    tables = this.admin.listTables();
    assertEquals(numTables + 1, tables.length);

    HTableDescriptor htd = this.admin.getTableDescriptor(tableName);

    HMaster master = TEST_UTIL.getMiniHBaseCluster().getMaster();

    // Try adding a column
    this.admin.enableTable(tableName);
    assertFalse(this.admin.isTableDisabled(tableName));
    final String xtracolName = "xtracol";
    HColumnDescriptor xtracol = new HColumnDescriptor(xtracolName);
    xtracol.setValue(xtracolName, xtracolName);
    boolean expectedException = false;
    try {
      this.admin.addColumn(tableName, xtracol);
    } catch (TableNotDisabledException re) {
      expectedException = true;
    }
    // Add column should work even if the table is enabled
    assertFalse(expectedException);

    // wait for all regions to reopen
    while (this.admin.getAlterStatus(tableName).getFirst() != 0) {
      Thread.sleep(100);
    }
    // get the table descriptor from META
    htd = this.admin.getTableDescriptor(tableName);
    List<Pair<HRegionInfo,HServerAddress>> regionToRegionServer = master.getTableRegions(tableName);
    // check if all regions have the column the correct schema.
    for (Pair<HRegionInfo, HServerAddress> p : regionToRegionServer) {
      HRegionInfo regionInfo = p.getFirst();  
      HTableDescriptor modifiedHtd = regionInfo.getTableDesc();
      // ensure that the Htable descriptor on the master and the region servers
      // of all regions is the same
      assertTrue(htd.equals(modifiedHtd));
    }

  }

  @Test(expected = IllegalArgumentException.class)
  public void testSplitKeysDoesNotHaveEmptyString() {
    // This test will check if the split keys provided during createTable,
    // does not have an empty string. Otherwise, there will be multiple regions
    // with start key as "".
    byte[][] splitKeys = {
      HConstants.EMPTY_BYTE_ARRAY,
      Bytes.toBytes("mmm")
    };
    admin.checkSplitKeys(splitKeys);
  }

}

