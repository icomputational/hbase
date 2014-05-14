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

package org.apache.hadoop.hbase.client;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.DoNotRetryIOException;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.NotServingRegionException;
import org.apache.hadoop.hbase.RemoteExceptionHandler;
import org.apache.hadoop.hbase.ipc.HBaseRPCOptions;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.StringBytes;
import org.apache.hadoop.ipc.RemoteException;


/**
 * Retries scanner operations such as create, next, etc.
 * Used by {@link ResultScanner}s made by {@link HTable}.
 */
public class ScannerCallable extends ServerCallable<Result[]> {
  private static final Log LOG = LogFactory.getLog(ScannerCallable.class);
  private long scannerId = -1L;
  private boolean closed = false;
  private Scan scan;
  private int caching = 1;
  private boolean forceReopen = false;
  private byte[] lastRowSeen = null;

  /**
   * @param connection which connection
   * @param tableName table callable is on
   * @param scan the scan to execute
   */
  public ScannerCallable(HConnection connection, StringBytes tableName,
      Scan scan, HBaseRPCOptions options) {
    super(connection, tableName, scan.getStartRow(), options);
    this.scan = scan;
    setCaching(scan.getCaching());
  }

  /**
   * @see java.util.concurrent.Callable#call()
   */
  @Override
  public Result [] call() throws IOException, InterruptedException, ExecutionException {
    if (scannerId != -1L && closed) {
      close();
    } else if (scannerId == -1L && !closed && !forceReopen) {
      this.scannerId = openScanner();
    } else {
      ensureScannerIsOpened();
      Result [] rrs = null;
      try {
        rrs = next();
      } catch (IOException e) {
        fixStateOrCancelRetryThanRethrow(e);
      }
      return rrs;
    }
    return null;
  }

  private void fixStateOrCancelRetryThanRethrow(IOException e) throws IOException {
    IOException ioe = null;
    if (e instanceof RemoteException) {
      ioe = RemoteExceptionHandler.decodeRemoteException((RemoteException) e);
    }
    if (ioe == null) {
      throw e;
    }
    if (ioe instanceof NotServingRegionException) {
      // Throw a DNRE so that we break out of cycle of calling NSRE
      // when what we need is to open scanner against new location.
      // Attach NSRE to signal client that it needs to resetup scanner.
      throw new DoNotRetryIOException("Reset scanner", ioe);
    } else {
      // The outer layers will retry
      fixStateForFutureRetries();
      throw ioe;
    }
  }

  private Result[] next() throws IOException {
    Result[] results = server.next(scannerId, caching);
    if (results != null && results.length > 0) {
      byte[] lastRow = results[results.length - 1].getRow();
      if (lastRow.length > 0) {
        lastRowSeen = lastRow;
      }
    }
    return results;
  }

  private void fixStateForFutureRetries() {
    LOG.info("Fixing scan state for future retries");
    close();
    if (lastRowSeen != null && lastRowSeen.length > 0) {
      scan.setStartRow(Bytes.nextOf(lastRowSeen));
    }
    forceReopen = true;
  }

  private void ensureScannerIsOpened() throws IOException {
    if (forceReopen) {
      scannerId = openScanner();
      forceReopen = false;
    }
  }

  private void close() {
    if (this.scannerId == -1L) {
      return;
    }
    try {
      this.server.close(this.scannerId);
    } catch (IOException e) {
      LOG.warn("Ignore, probably already closed", e);
    }
    this.scannerId = -1L;
  }

  protected long openScanner() throws IOException {
    return this.server.openScanner(this.location.getRegionInfo().getRegionName(),
      this.scan);
  }

  protected Scan getScan() {
    return scan;
  }

  /**
   * Call this when the next invocation of call should close the scanner
   */
  public void setClose() {
    this.closed = true;
  }

  /**
   * @return the HRegionInfo for the current region
   */
  public HRegionInfo getHRegionInfo() {
    if (location == null) {
      return null;
    }
    return location.getRegionInfo();
  }

  /**
   * Get the number of rows that will be fetched on next
   * @return the number of rows for caching
   */
  public int getCaching() {
    return caching;
  }

  /**
   * Set the number of rows that will be fetched on next
   * @param caching the number of rows for caching
   */
  public void setCaching(int caching) {
    this.caching = caching;
  }
}
