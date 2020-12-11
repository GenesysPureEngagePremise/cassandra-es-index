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

import com.genesyslab.webme.commons.index.config.IndexConfig.Segment;
import com.genesyslab.webme.commons.index.requests.ElasticClientFactory;
import com.genesyslab.webme.commons.index.test.JestClientFactoryMock;
import com.genesyslab.webme.commons.index.test.JestClientMock;

import com.google.gson.JsonObject;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.PartitionRangeReadCommand;
import org.apache.cassandra.db.PreHashedDecoratedKey;
import org.apache.cassandra.db.filter.RowFilter;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.dht.Murmur3Partitioner;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.exceptions.IsBootstrappingException;
import org.apache.cassandra.exceptions.ReadFailureException;
import org.apache.cassandra.exceptions.ReadTimeoutException;
import org.apache.cassandra.exceptions.UnavailableException;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.searchbox.client.JestResult;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Vincent Pirat 01/12/2016
 */
public class EsSecondaryIndexTest {

  private static EsSecondaryIndexUnderTest secondaryIndex;

  @BeforeClass
  public static void setup() throws Exception {
    System.setProperty("genesys-es-unicast-hosts", "");
    System.setProperty("genesys-es-<datacenter1.rack1>.unicast-hosts", "https://mars:2048");
    DatabaseDescriptor.clientInitialization(false);
    ElasticClientFactory.setJestClientFactory(JestClientFactoryMock.INSTANCE);
    JestResult cluster = mock(JestResult.class);
    when(cluster.isSucceeded()).thenReturn(true);
    JestClientMock.addResponse(cluster);

    JestResult getVersion = mock(JestResult.class);
    when(getVersion.isSucceeded()).thenReturn(true);
    JsonObject jsonIndex = new JsonObject();
    JsonObject version = new JsonObject();
    version.addProperty("number", "7.9.2");
    jsonIndex.add("version", version);
    when(getVersion.getJsonObject()).thenReturn(jsonIndex);
    JestClientMock.addResponse(getVersion);
    secondaryIndex = new EsSecondaryIndexUnderTest();
  }

  @AfterClass
  public static void cleanup() {
    JestClientMock.clear();
    ElasticClientFactory.setJestClientFactory(null);
    System.setProperty("genesys-es-<datacenter1.rack1>.unicast-hosts", "");
  }

  @Before
  public void clean() {
    JestClientMock.clear();
  }

  @Test
  public void shouldSetCustomConfig() throws ReadTimeoutException, ReadFailureException, UnavailableException, IsBootstrappingException,
    InvalidRequestException {

    assertThat(secondaryIndex.name, is("EsSecondaryIndex [demo.testindex]"));

    String options = "#update#{"
      + "\"read-consistency-level\":\"LOCAL_QUORUM\","
      + "\"insert-only\":\"false\","
      + "\"discard-nulls\":\"false\","
      + "\"async-search\":\"true\","
      + "\"async-write\":\"false\","
      + "\"class_name\": \"com.genesyslab.webme.commons.index.EsSecondaryIndex\","
      + "\"segment\": \"CUSTOM\","
      + "\"segment-name\": \"segmentName\","
      + "\"index-properties\": \"{"
      + "\\\"script.disable_dynamic\\\":\\\"false\\\","
      + "\\\"index.refresh_interval\\\":\\\"1s\\\","
      + "\\\"analysis.analyzer.html_analyzer.char_filter\\\":\\\"html_strip\\\","
      + "\\\"analysis.analyzer.html_analyzer.type\\\":\\\"custom\\\","
      + "\\\"analysis.analyzer.html_analyzer.filter\\\":\\\"standard\\\","
      + "\\\"analysis.analyzer.html_analyzer.tokenizer\\\":\\\"standard\\\"}\"}#";

    RowFilter rowFilter = RowFilter.create();
    rowFilter.addCustomIndexExpression(EsSecondaryIndexUnderTest.cfMetaData, EsSecondaryIndexUnderTest.indexMetadata,
      ByteBufferUtil.bytes(options, UTF_8));

    PartitionRangeReadCommand command = mock(PartitionRangeReadCommand.class);
    when(command.rowFilter()).thenReturn(rowFilter);

    try {
      secondaryIndex.search(command);
    } catch (RuntimeException e) {
      //If we went as far as this it means we reached MigrationManager !
      assertEquals("java.util.concurrent.ExecutionException: java.lang.AssertionError: Unknown keyspace system_schema", e.getMessage());

      //Little workaround to set the metadata column with the new index value.
      secondaryIndex.reloadSettings();
    }

    assertThat(secondaryIndex.esIndex, is(notNullValue()));
    assertThat(secondaryIndex.indexConfig.isInsertOnly(), is(false));
    assertThat(secondaryIndex.indexConfig.getIndexSegmentName(), is("segmentName"));
    assertThat(secondaryIndex.indexConfig.getIndexSegment(), is(Segment.CUSTOM));
    assertThat(secondaryIndex.indexConfig.getReadConsistencyLevel(), is(ConsistencyLevel.LOCAL_QUORUM));
    assertThat(secondaryIndex.indexConfig.getMaxResults(), is(10000));
    assertThat(secondaryIndex.indexConfig.getUnicastHosts(), is("https://mars:2048"));
    assertThat(secondaryIndex.indexConfig.isConcurrentLock(), is(true));
    assertThat(secondaryIndex.indexConfig.isSkipNonLocalUpdates(), is(true));
    assertThat(secondaryIndex.indexConfig.isAsyncWrite(), is(false));
    assertThat(secondaryIndex.indexConfig.getJsonFlatSerializedFields().size(), is(0));
    assertThat(secondaryIndex.indexConfig.getJsonSerializedFields().size(), is(0));

    assertThat(secondaryIndex.indexConfig.getIndexOptions().size(), greaterThanOrEqualTo(10));

    assertThat(secondaryIndex.indexConfig.getIndexOptions().get("index-properties"), is("{"
      + "\"script.disable_dynamic\":\"false\","
      + "\"index.refresh_interval\":\"1s\","
      + "\"analysis.analyzer.html_analyzer.char_filter\":\"html_strip\","
      + "\"analysis.analyzer.html_analyzer.type\":\"custom\","
      + "\"analysis.analyzer.html_analyzer.filter\":\"standard\","
      + "\"analysis.analyzer.html_analyzer.tokenizer\":\"standard\"}"));
    assertThat(secondaryIndex.indexConfig.getIndexOptions().get("segment"), is("CUSTOM"));
    assertThat(secondaryIndex.indexConfig.getIndexOptions().get("segment-name"), is("segmentName"));
    assertThat(secondaryIndex.indexConfig.getIndexOptions().get("class_name"), is("com.genesyslab.webme.commons.index.EsSecondaryIndex"));
    assertThat(secondaryIndex.indexConfig.getIndexOptions().get("discard-nulls"), is("false"));
    assertThat(secondaryIndex.indexConfig.getIndexOptions().get("read-consistency-level"), is("LOCAL_QUORUM"));
    assertThat(secondaryIndex.indexConfig.getIndexOptions().get("async-search"), is("true"));
    assertThat(secondaryIndex.indexConfig.getIndexOptions().get("insert-only"), is("false"));
    assertThat(secondaryIndex.indexConfig.getIndexOptions().get("async-write"), is("false"));
  }


  @Test
  public void testEmptyUpdateDoesNotDelete() { //UCS-4927
    DecoratedKey key = new PreHashedDecoratedKey(new Murmur3Partitioner.LongToken(0), ByteBufferUtil.bytes(0), 1, 2);
    Row row = mock(Row.class);
    when(row.cells()).thenReturn(emptyList());

    secondaryIndex.index(key, row, null, 0);

    assertThat(JestClientMock.receivedRequests.size(), is(0)); //no delete is sent to ES
  }
}

