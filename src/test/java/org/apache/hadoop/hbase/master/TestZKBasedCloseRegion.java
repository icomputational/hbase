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
package org.apache.hadoop.hbase.master;


import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.HMsg;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.HServerInfo;
import org.apache.hadoop.hbase.MediumTests;
import org.apache.hadoop.hbase.MiniHBaseCluster;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.regionserver.HRegion;
import org.apache.hadoop.hbase.regionserver.HRegionServer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Writables;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(MediumTests.class)
public class TestZKBasedCloseRegion {
  private static final Log LOG = LogFactory.getLog(TestZKBasedCloseRegion.class);
  private static final HBaseTestingUtility TEST_UTIL = new HBaseTestingUtility();
  private static final String TABLENAME = "master_transitions";
  private static final byte [][] FAMILIES = new byte [][] {Bytes.toBytes("a"),
    Bytes.toBytes("b"), Bytes.toBytes("c")};

  @BeforeClass public static void beforeAllTests() throws Exception {
    Configuration c = TEST_UTIL.getConfiguration();
    c.setBoolean("dfs.support.append", true);
    c.setInt(HConstants.REGIONSERVER_INFO_PORT, 0);
    c.setInt("hbase.master.meta.thread.rescanfrequency", 5*1000);
    TEST_UTIL.startMiniCluster(2);

    int countOfRegions = 25;
    TEST_UTIL.createTable(Bytes.toBytes(TABLENAME), FAMILIES, 3,
        Bytes.toBytes("bbb"), Bytes.toBytes("yyy"), countOfRegions);

    addToEachStartKey(countOfRegions);
  }

  @AfterClass public static void afterAllTests() throws IOException {
    TEST_UTIL.shutdownMiniCluster();
  }

  @Before public void setup() throws IOException {
    if (TEST_UTIL.getHBaseCluster().getLiveRegionServerThreads().size() < 2) {
      // Need at least two servers.
      LOG.info("Started new server=" +
        TEST_UTIL.getHBaseCluster().startRegionServer());

    }
  }

  @Test (timeout=300000) public void testCloseRegion()
  throws Exception {
    LOG.info("Running testCloseRegion");
    MiniHBaseCluster cluster = TEST_UTIL.getHBaseCluster();
    LOG.info("Number of region servers = " + cluster.getLiveRegionServerThreads().size());

    int rsIdx = 0;
    HRegionServer regionServer = TEST_UTIL.getHBaseCluster().getRegionServer(rsIdx);
    Collection<HRegion> regions = regionServer.getOnlineRegions();
    HRegion region = regions.iterator().next();
    LOG.debug("Asking RS to close region " + region.getRegionNameAsString());

    AtomicBoolean closeEventProcessed = new AtomicBoolean(false);
    RegionServerOperationListener listener =
      new CloseRegionEventListener(region.getRegionNameAsString(), closeEventProcessed);
    HMaster master = TEST_UTIL.getHBaseCluster().getMaster();
    master.getRegionServerOperationQueue().registerRegionServerOperationListener(listener);
    HMsg closeRegionMsg = new HMsg(HMsg.Type.MSG_REGION_CLOSE,
                                   region.getRegionInfo(),
                                   Bytes.toBytes("Forcing close in test")
                                  );
    TEST_UTIL.getHBaseCluster().addMessageToSendRegionServer(rsIdx, closeRegionMsg);

    synchronized(closeEventProcessed) {
      // wait for 3 minutes
      closeEventProcessed.wait(3*60*1000);
    }
    if(!closeEventProcessed.get()) {
      throw new Exception("Timed out, close event not called on master.");
    }
    else {
      LOG.info("Done with test, RS informed master successfully.");
    }
  }

  public static class CloseRegionEventListener implements RegionServerOperationListener {

    private static final Log LOG = LogFactory.getLog(CloseRegionEventListener.class);
    String regionToClose;
    AtomicBoolean closeEventProcessed;

    public CloseRegionEventListener(String regionToClose, AtomicBoolean closeEventProcessed) {
      this.regionToClose = regionToClose;
      this.closeEventProcessed = closeEventProcessed;
    }

    @Override
    public boolean process(HServerInfo serverInfo, HMsg incomingMsg) {
      return true;
    }

    @Override
    public boolean process(RegionServerOperation op) throws IOException {
      return true;
    }

    @Override
    public void processed(RegionServerOperation op) {
      LOG.debug("Master processing object: " + op.getClass().getCanonicalName());
      if(op instanceof ProcessRegionClose) {
        ProcessRegionClose regionCloseOp = (ProcessRegionClose)op;
        String region = regionCloseOp.getRegionInfo().getRegionNameAsString();
        LOG.debug("Finished closing region " + region + ", expected to close region " + regionToClose);
        if(regionToClose.equals(region)) {
          closeEventProcessed.set(true);
        }
        synchronized(closeEventProcessed) {
          closeEventProcessed.notifyAll();
        }
      }
    }

  }


  /*
   * Add to each of the regions in .META. a value.  Key is the startrow of the
   * region (except its 'aaa' for first region).  Actual value is the row name.
   * @param expected
   * @return
   * @throws IOException
   */
  private static int addToEachStartKey(final int expected) throws IOException {
    HTable t = new HTable(TEST_UTIL.getConfiguration(), TABLENAME);
    HTable meta = new HTable(TEST_UTIL.getConfiguration(),
        HConstants.META_TABLE_NAME);
    int rows = 0;
    Scan scan = new Scan();
    scan.addColumn(HConstants.CATALOG_FAMILY, HConstants.REGIONINFO_QUALIFIER);
    ResultScanner s = meta.getScanner(scan);
    for (Result r = null; (r = s.next()) != null;) {
      byte [] b =
        r.getValue(HConstants.CATALOG_FAMILY, HConstants.REGIONINFO_QUALIFIER);
      if (b == null || b.length <= 0) break;
      HRegionInfo hri = Writables.getHRegionInfo(b);
      // If start key, add 'aaa'.
      byte [] row = getStartKey(hri);
      Put p = new Put(row);
      p.add(getTestFamily(), getTestQualifier(), row);
      t.put(p);
      rows++;
    }
    s.close();
    Assert.assertEquals(expected, rows);
    return rows;
  }

  private static byte [] getStartKey(final HRegionInfo hri) {
    return Bytes.equals(HConstants.EMPTY_START_ROW, hri.getStartKey())?
        Bytes.toBytes("aaa"): hri.getStartKey();
  }

  private static byte [] getTestFamily() {
    return FAMILIES[0];
  }

  private static byte [] getTestQualifier() {
    return getTestFamily();
  }

  public static void main(String args[]) throws Exception {
    TestZKBasedCloseRegion.beforeAllTests();

    TestZKBasedCloseRegion test = new TestZKBasedCloseRegion();
    test.setup();
    test.testCloseRegion();

    TestZKBasedCloseRegion.afterAllTests();
  }
}
