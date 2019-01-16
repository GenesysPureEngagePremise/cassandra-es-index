/*
 * Copyright 2019 Genesys Telecommunications Laboratories, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.genesyslab.webme.commons.index.indexers;

import com.genesyslab.webme.commons.index.EsSecondaryIndex;

import com.google.common.base.Stopwatch;

import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.DeletionTime;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * This indexer handles inserts and updates but discards deletes
 * <p>
 * <p>
 * Created by Jacques-Henri Berthemet on 05/07/2017.
 */
public class EsIndexer extends NoOpIndexer {

  private static final Logger LOGGER = LoggerFactory.getLogger(EsIndexer.class);

  private final EsSecondaryIndex index;
  private final DecoratedKey key;
  private final int nowInSec;
  private final String id;
  private final boolean delete;

  public EsIndexer(EsSecondaryIndex index, DecoratedKey key, int nowInSec, boolean withDelete) {
    this.key = key;
    this.nowInSec = nowInSec;
    this.index = index;
    this.id = ByteBufferUtil.bytesToHex(key.getKey());
    this.delete = withDelete;
  }

  // see https://opencredo.com/cassandra-tombstones-common-issues/
  // UCS-5008, WCC-1344 rangeTombstone and removeRow do nothing

  @Override
  public void insertRow(Row row) {
    Stopwatch time = Stopwatch.createStarted();
    index.index(key, row, null, nowInSec);
    LOGGER.debug("{} insertRow {} took {}ms", index.name, id, time.elapsed(TimeUnit.MILLISECONDS));
  }

  @Override
  public void updateRow(Row oldRowData, Row newRowData) {
    Stopwatch time = Stopwatch.createStarted();
    index.index(key, newRowData, oldRowData, nowInSec);
    LOGGER.debug("{} updateRow {} took {}ms", index.name, id, time.elapsed(TimeUnit.MILLISECONDS));
  }

  @Override
  public void partitionDelete(DeletionTime deletionTime) {
    if (delete) {
      Stopwatch time = Stopwatch.createStarted();
      index.delete(key);
      LOGGER.debug("{} partitionDelete {} took {}ms", index.name, id, time.elapsed(TimeUnit.MILLISECONDS));
    }
  }
}
