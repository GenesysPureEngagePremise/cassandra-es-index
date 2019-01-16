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

import com.genesyslab.webme.commons.index.config.IndexConfig;
import com.genesyslab.webme.commons.index.config.IndexConfiguration;
import com.genesyslab.webme.commons.index.requests.ElasticClientFactory;
import com.genesyslab.webme.commons.index.test.JestClientFactoryMock;
import com.genesyslab.webme.commons.index.test.JestClientMock;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.junit.Assert.assertThat;

/**
 * @author Jacques-Henri Berthemet 25/10/2018
 */
public class ElasticIndexConfigTest {

  private static final String KEYSPACE_NAME = "testKeyspace";
  private static final String TABLE_NAME = "testTable";

  @BeforeClass
  public static void setupClient() {
    EsSecondaryIndexUnderTest.staticInit();
    ElasticClientFactory.setJestClientFactory(JestClientFactoryMock.INSTANCE);
  }

  @AfterClass
  public static void cleanup() {
    JestClientMock.clear();
    ElasticClientFactory.setJestClientFactory(null);
  }


  @Test
  public void shouldSetEsHostNames() {
    Map<String, String> options = new HashMap<>();
    options.put("<datacenter1>.unicast-hosts", "jupiter,mars");
    IndexConfig indexConfig = new IndexConfiguration(TABLE_NAME, options);
    ElasticIndex indexUnderTest = new ElasticIndex(indexConfig, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());
    indexUnderTest.init();

    assertThat(JestClientFactoryMock.httpConfig.getServerList(), hasItems("http://jupiter:9200", "http://mars:9200"));
    assertThat(JestClientMock.receivedRequests.size(), is(2));
    assertThat(JestClientMock.receivedRequests.get(0).toString(),
      is("Health{uri=/_cluster/health/_all?wait_for_status=yellow, method=GET}"));
    assertThat(JestClientMock.receivedRequests.get(1).toString(), is("IndicesExists{uri=testkeyspace_testtable_index%40, method=HEAD}"));
  }
}
