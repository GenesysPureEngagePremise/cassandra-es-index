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
package com.genesyslab.webme.commons.index;

import com.google.common.collect.ImmutableMap;

import org.apache.cassandra.config.CFMetaData;
import org.apache.cassandra.config.ColumnDefinition;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.config.ParameterizedClass;
import org.apache.cassandra.config.Schema;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.Directories;
import org.apache.cassandra.db.Keyspace;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.dht.Murmur3Partitioner;
import org.apache.cassandra.schema.IndexMetadata;
import org.apache.cassandra.schema.KeyspaceMetadata;
import org.apache.cassandra.schema.KeyspaceParams;
import org.apache.cassandra.schema.ReplicationParams;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.apache.cassandra.config.ColumnDefinition.Kind.REGULAR;
import static org.apache.cassandra.config.ColumnDefinition.NO_POSITION;
import static org.apache.cassandra.cql3.statements.IndexTarget.CUSTOM_INDEX_OPTION_NAME;
import static org.apache.cassandra.cql3.statements.IndexTarget.TARGET_OPTION_NAME;

/**
 * @author Vincent Pirat 01/12/2016
 */
public class EsSecondaryIndexUnderTest extends EsSecondaryIndex {

  static { //Make sure you don't change static block order: this block is just after class def on top
    try {
      DatabaseDescriptor.clientInitialization(true);
      DatabaseDescriptor.setEndpointSnitch(DatabaseDescriptor.createEndpointSnitch(false, "SimpleSnitch"));
      DatabaseDescriptor.setPartitionerUnsafe(Murmur3Partitioner.instance);

      TemporaryFolder folder = new TemporaryFolder();
      folder.create();
      File tempDir = folder.newFolder("CassandraTempFiles");

      Config conf = DatabaseDescriptor.getRawConfig();
      conf.memtable_flush_writers = 1;
      conf.concurrent_compactors = 1;
      conf.commitlog_sync = Config.CommitLogSync.periodic;
      conf.commitlog_sync_period_in_ms = 100000;
      conf.partitioner = "RandomPartitioner";
      conf.endpoint_snitch = "SimpleSnitch";
      conf.commitlog_directory = tempDir.getCanonicalPath().concat("/commit");
      conf.saved_caches_directory = tempDir.getCanonicalPath().concat("/cache");
      conf.data_file_directories = new String[] {tempDir.getCanonicalPath()};

      conf.seed_provider = new ParameterizedClass("org.apache.cassandra.locator.SimpleSeedProvider",
        ImmutableMap.<String, String>builder()
          .put("seeds", "localhost").build());

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static final String keyspaceName = "demo";
  private static final String tableName = "tutu";
  private static final String TEST_INDEX = "testindex";
  private static final String ES_COL = "esquery";
  private static final String ID_COL = "id";
  private static final String VALUE_COL = "value";

  private static final Keyspace keyspace = Keyspace.mockKS(KeyspaceMetadata.create(keyspaceName,
    new KeyspaceParams(false, ReplicationParams.fromMap(
      ImmutableMap.<String, String>builder()
        .put("class", "SimpleStrategy")
        .put("replication_factor", "3")
        .build()))));

  static final CFMetaData cfMetaData = CFMetaData.Builder.create(keyspaceName, tableName)
    .withPartitioner(Murmur3Partitioner.instance)
    .addPartitionKey(ID_COL, UTF8Type.instance)
    .addRegularColumn(VALUE_COL, UTF8Type.instance)
    .addRegularColumn(ES_COL, UTF8Type.instance)
    .build();

  private static final ColumnFamilyStore baseCfs = new ColumnFamilyStore(keyspace, tableName, 0, cfMetaData,
    new Directories(cfMetaData), false, false, true);

  private static final Map<String, String> options = ImmutableMap.<String, String>builder()
    .put(CUSTOM_INDEX_OPTION_NAME, "com.genesyslab.webme.commons.index.EsSecondaryIndex")
    .put(TARGET_OPTION_NAME, tableName)
    .put("async-write", "false")
    .build();

  static final IndexMetadata indexMetadata;

  static { //Make sure you don't change static block order: this block is just before constructor
    Keyspace.setInitialized();
    Schema.instance.storeKeyspaceInstance(keyspace);
    Schema.instance.addKeyspace(keyspace.getMetadata());
    Schema.instance.addTable(cfMetaData);

    ColumnDefinition idxColDef = new ColumnDefinition(cfMetaData, ByteBufferUtil.bytes(ES_COL), UTF8Type.instance, NO_POSITION, REGULAR);
    indexMetadata = IndexMetadata.fromLegacyMetadata(cfMetaData, idxColDef, TEST_INDEX, IndexMetadata.Kind.CUSTOM, options);
    cfMetaData.indexes(cfMetaData.getIndexes().with(indexMetadata));
  }

  public static void staticInit() {
  }

  public EsSecondaryIndexUnderTest() throws Exception {
    super(baseCfs, indexMetadata);
  }

}
