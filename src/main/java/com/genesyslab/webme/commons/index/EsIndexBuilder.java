/*
 * Copyright 2019 Genesys Telecommunications Laboratories, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.genesyslab.webme.commons.index;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.compaction.CompactionInfo;
import org.apache.cassandra.db.compaction.CompactionInterruptedException;
import org.apache.cassandra.db.compaction.OperationType;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.index.SecondaryIndexBuilder;
import org.apache.cassandra.io.sstable.format.SSTableReader;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.UUIDGen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

/**
 * Index building task that reads all live SSTables and index the content
 * <p>
 * Created by Jacques-Henri Berthemet on 10/07/2017.
 */
public class EsIndexBuilder extends SecondaryIndexBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(EsIndexBuilder.class);

  private final UUID compactionId = UUIDGen.getTimeUUID();
  private final EsSecondaryIndex index;
  private final Collection<SSTableReader> ssTables;
  private final long total;
  private long processed = 0;

  EsIndexBuilder(EsSecondaryIndex index) {
    this(index, index.baseCfs.getLiveSSTables());
  }

  EsIndexBuilder(EsSecondaryIndex index, Collection<SSTableReader> ssTables) {
    this.index = index;
    this.ssTables = ssTables;
    this.total = ssTables.stream().mapToLong(SSTableReader::getTotalRows).sum();
  }

  @Override
  public void build() {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    LOGGER.info("{} build {} starting on {} ssTables with {} rows to index", index.name, compactionId, ssTables.size(), total);

    if (index.indexConfig.isTruncateBeforeRebuild()) {
      index.esIndex.truncate();
    }

    // For each of the SSTables, we get a partition scanner, for each partition we get its row and we
    // index it
    ssTables.forEach(ssTableReader -> ssTableReader.getScanner().forEachRemaining(partition -> {
      partition.forEachRemaining(row -> {
        if (isStopRequested()) {
          LOGGER.warn("{} build {} stop requested {}/{} rows", index.name, compactionId, processed, total);
          throw new CompactionInterruptedException(getCompactionInfo());
        }

        DecoratedKey key = partition.partitionKey();
        if (row instanceof Row) { // not sure what else it could be
          index.index(key, (Row) row, null, FBUtilities.nowInSeconds());
        } else {
          LOGGER.warn("{} build {} skipping unsupported {} {}", index.name, compactionId, row.getClass().getName(), key);
        }
        processed++;
      });
    }));

    LOGGER.info("{} build {} completed in {} minutes for {} rows", index.name, compactionId, stopwatch.elapsed(TimeUnit.MINUTES), total);
  }

  @Override
  public CompactionInfo getCompactionInfo() {
    return CompactionInfo.withoutSSTables(null, OperationType.INDEX_BUILD, processed, total, null, compactionId);
  }
}
