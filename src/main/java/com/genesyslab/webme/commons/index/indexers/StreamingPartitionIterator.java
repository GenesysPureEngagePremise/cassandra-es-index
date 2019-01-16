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
import com.genesyslab.webme.commons.index.JsonUtils;
import com.genesyslab.webme.commons.index.SearchResult;
import com.genesyslab.webme.commons.index.SearchResultRow;

import com.google.gson.JsonObject;

import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.PartitionRangeReadCommand;
import org.apache.cassandra.db.SinglePartitionReadCommand;
import org.apache.cassandra.db.filter.DataLimits;
import org.apache.cassandra.db.filter.RowFilter;
import org.apache.cassandra.db.partitions.PartitionIterator;
import org.apache.cassandra.db.partitions.UnfilteredPartitionIterator;
import org.apache.cassandra.db.rows.BTreeRow;
import org.apache.cassandra.db.rows.BufferCell;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.db.rows.RowIterator;
import org.apache.cassandra.db.rows.UnfilteredRowIterator;
import org.apache.cassandra.service.StorageProxy;
import org.apache.cassandra.tracing.Tracing;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.cassandra.utils.FBUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.Iterator;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This a partition iterator that will read a row each time next() is called, should be the lightest on resources but maybe the slowest.<br>
 * This is the equivalent of the sync read mode
 * <p>
 * Created by Jacques-Henri Berthemet on 11/07/2017.
 */
public class StreamingPartitionIterator implements UnfilteredPartitionIterator {

  private static final Logger LOGGER = LoggerFactory.getLogger(StreamingPartitionIterator.class);

  private final Iterator<SearchResultRow> esResultIterator;
  private final ColumnFamilyStore baseCfs;
  private final PartitionRangeReadCommand command;
  private final String searchId;
  private final EsSecondaryIndex index;
  private final ColumnDefinition indexColDef;
  private final ConsistencyLevel consistencyLevel;
  private final JsonObject searchResultMetadata;
  private final boolean metadataRequested;
  private boolean isFirst = true;

  public StreamingPartitionIterator(EsSecondaryIndex index, SearchResult searchResult,
    PartitionRangeReadCommand command, String searchId) {
    this.baseCfs = index.baseCfs;
    this.esResultIterator = searchResult.items.iterator();
    this.command = command;
    this.searchId = searchId;
    this.index = index;
    this.indexColDef = index.getIndexColDef();
    this.consistencyLevel = index.getReadConsistency();
    this.searchResultMetadata = searchResult.metadata;
    this.metadataRequested = command.columnFilter().queriedColumns().contains(indexColDef);
    Tracing.trace("ESI {} StreamingPartitionIterator initialized", searchId);
  }

  @Override
  public boolean isForThrift() {
    return command.isForThrift();
  }

  @Override
  public CFMetaData metadata() {
    return command.metadata();
  }

  @Override
  public void close() {
    Tracing.trace("ESI {} StreamingPartitionIterator closed", searchId);
  }

  @Override
  public boolean hasNext() {
    return esResultIterator.hasNext();
  }

  @Override
  public UnfilteredRowIterator next() {
    JsonObject jsonMetadata = null;
    RowIterator rowIterator = null;
    Row row = null;


    while (esResultIterator.hasNext() && row == null) {
      SearchResultRow esResult = esResultIterator.next();
      jsonMetadata = esResult.docMetadata;
      DecoratedKey partitionKey = baseCfs.getPartitioner().decorateKey(esResult.partitionKey);

      SinglePartitionReadCommand readCommand = SinglePartitionReadCommand.create(
        baseCfs.metadata,
        command.nowInSec(),
        command.columnFilter(), //columns that will be returned
        RowFilter.NONE, //don't filter anything, as we pass token(id) it may prevent loading non local rows
        DataLimits.NONE, //don't use command DataLimits because we are only loading one partition
        partitionKey,
        command.clusteringIndexFilter(partitionKey));

      //Cassandra has below method but not DSE:
      // PartitionIterator partition = readCommand.execute(consistencyLevel, ClientState.forInternalCalls(), System.nanoTime());
      // WCC-1131 Call directly this method for it is available both in open-source cassandra and in Datastax Enterprise
      PartitionIterator partition =
        StorageProxy.read(SinglePartitionReadCommand.Group.one(readCommand), consistencyLevel, System.nanoTime());

      if (!partition.hasNext()) {
        logRowNotFound(partitionKey);
        continue;
      }

      rowIterator = partition.next();
      if (!rowIterator.hasNext()) {
        logRowNotFound(partitionKey);
        continue;
      }

      row = rowIterator.next(); //FIXME clustered partitions will contain several rows
    }

    if (row == null) { //if all ES results were expired
      return null;
    }

    if (metadataRequested) { //Rewrite only if metadata requested
      if (isFirst) {  //only first result have global metadata
        jsonMetadata = JsonUtils.mergeJson(searchResultMetadata, jsonMetadata);
        isFirst = false;
      }

      if (jsonMetadata != null) {
        int now = FBUtilities.nowInSeconds();
        Row.Builder rowBuilder = BTreeRow.unsortedBuilder(now);

        rowBuilder.newRow(row.clustering());  //need to be first
        rowBuilder.addPrimaryKeyLivenessInfo(row.primaryKeyLivenessInfo());
        rowBuilder.addRowDeletion(row.deletion());

        //copy existing cells
        row.cells().forEach(cell -> {
          if (!index.indexColumnName.equals(cell.column().name.toString())) {
            rowBuilder.addCell(cell);
          }
        });

        //add metadata cell
        ByteBuffer value = ByteBufferUtil.bytes(jsonMetadata.toString(), UTF_8);
        BufferCell metadataCell = BufferCell.live(indexColDef, now, value);

        rowBuilder.addCell(metadataCell);
        row = rowBuilder.build();
      }
    }

    return new SingleRowIterator(rowIterator, row);
  }

  private void logRowNotFound(DecoratedKey partitionKey) {
    String id;
    try {
      id = ByteBufferUtil.string(partitionKey.getKey());
    } catch (CharacterCodingException e) {
      id = e.getMessage();
    }
    LOGGER.warn("Search {} can't load '{}' no such row found", searchId, id);
  }
}
