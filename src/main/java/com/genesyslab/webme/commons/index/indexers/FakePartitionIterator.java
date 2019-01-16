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
import org.apache.cassandra.db.Clustering;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.LivenessInfo;
import org.apache.cassandra.db.PartitionColumns;
import org.apache.cassandra.db.ReadCommand;
import org.apache.cassandra.db.partitions.UnfilteredPartitionIterator;
import org.apache.cassandra.db.rows.BTreeRow;
import org.apache.cassandra.db.rows.BufferCell;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.db.rows.UnfilteredRowIterator;
import org.apache.cassandra.tracing.Tracing;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.cassandra.utils.FBUtilities;

import java.nio.ByteBuffer;
import java.util.Iterator;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This iterator does not load data from Cassandra and will only return PKs and ES metadata
 *
 * @author Jacques-Henri Berthemet 04/08/2017
 */
public class FakePartitionIterator implements UnfilteredPartitionIterator {

  private static final String FAKE_METADATA = "{\"metadata\":\"none\"}"; //so that it's always json

  private final Iterator<SearchResultRow> esResultIterator;
  private final ColumnFamilyStore baseCfs;
  private final ReadCommand command;
  private final String searchId;
  private final ColumnDefinition indexColDef;
  private final PartitionColumns returnedColumns;
  private final JsonObject searchResultMetadata;
  private final boolean metadataRequested;
  private boolean isFirst = true;

  public FakePartitionIterator(EsSecondaryIndex index, SearchResult searchResult, ReadCommand command, String searchId) {
    this.baseCfs = index.baseCfs;
    this.esResultIterator = searchResult.items.iterator();
    this.command = command;
    this.searchId = searchId;
    this.indexColDef = index.getIndexColDef();
    this.returnedColumns = PartitionColumns.builder().add(indexColDef).build(); //can build once, it's always the same
    this.searchResultMetadata = searchResult.metadata;
    this.metadataRequested = command.columnFilter().queriedColumns().contains(indexColDef);
    Tracing.trace("ESI {} FakePartitionIterator initialized", searchId);
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
    Tracing.trace("ESI {} FakePartitionIterator closed", searchId);
  }

  @Override
  public boolean hasNext() {
    return esResultIterator.hasNext();
  }

  @Override
  public UnfilteredRowIterator next() {
    if (!esResultIterator.hasNext()) {
      return null;
    }

    //Build the minimum row
    Row.Builder rowBuilder = BTreeRow.unsortedBuilder(FBUtilities.nowInSeconds());
    rowBuilder.newRow(Clustering.EMPTY); //FIXME support for clustering
    rowBuilder.addPrimaryKeyLivenessInfo(LivenessInfo.EMPTY);
    rowBuilder.addRowDeletion(Row.Deletion.LIVE);

    SearchResultRow esResult = esResultIterator.next();

    if (metadataRequested) { //Add the metadata cell
      JsonObject jsonMetadata = esResult.docMetadata;
      if (isFirst) { //only first result have global metadata
        jsonMetadata = JsonUtils.mergeJson(searchResultMetadata, jsonMetadata);
        isFirst = false;
      }

      ByteBuffer value = ByteBufferUtil.bytes(jsonMetadata == null ? FAKE_METADATA : jsonMetadata.toString(), UTF_8);
      BufferCell metadataCell = BufferCell.live(indexColDef, FBUtilities.nowInSeconds(), value);
      rowBuilder.addCell(metadataCell);
    }

    //And PK value
    DecoratedKey partitionKey = baseCfs.getPartitioner().decorateKey(esResult.partitionKey);
    return new SingleRowIterator(baseCfs.metadata, rowBuilder.build(), partitionKey, returnedColumns);
  }
}
