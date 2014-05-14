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

import java.io.Closeable;
import java.io.IOException;

/**
 * Interface for client-side scanning.
 * Go to {@link HTable} to obtain instances.
 */
public interface ResultScanner extends Closeable, Iterable<Result> {

  /**
   * Grabs the next row's worth of values. The scanner will return a Result.
   * The caller may call this method even if the scanner is closed.
   *
   * @return Result object if there is another row, null if the scanner is
   *         exhausted.
   */
  public Result next() throws IOException;

  /**
   * Returns up to nbRows of the results. The implementation should return at
   * least one result unless the scanner is exhausted or closed.
   *
   * If no results are available, a null or empty array is returned.
   *
   * @param nbRows the maximum number of rows to return
   * @return Between zero and <param>nbRows</param> Results
   */
  public Result[] next(int nbRows) throws IOException;

  /**
   * Closes the scanner and releases any resources it has allocated
   */
  @Override
  public void close();

  /**
   * @return true if the scanner is closed. Otherwise return false.
   */
  public boolean isClosed();
}
