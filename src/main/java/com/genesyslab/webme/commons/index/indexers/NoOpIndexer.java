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

import org.apache.cassandra.db.DeletionTime;
import org.apache.cassandra.db.RangeTombstone;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.index.Index;

/**
 * This indexer does nothing but can be extended
 * <p>
 * Created by Jacques-Henri Berthemet on 05/07/2017.
 */
public class NoOpIndexer implements Index.Indexer {

  public static final NoOpIndexer INSTANCE = new NoOpIndexer();

  @Override
  public void begin() {
  }

  @Override
  public void partitionDelete(DeletionTime deletionTime) {
  }

  @Override
  public void rangeTombstone(RangeTombstone tombstone) {
  }

  @Override
  public void insertRow(Row row) {
  }

  @Override
  public void updateRow(Row oldRowData, Row newRowData) {
  }

  @Override
  public void removeRow(Row row) {
  }

  @Override
  public void finish() {
  }
}
