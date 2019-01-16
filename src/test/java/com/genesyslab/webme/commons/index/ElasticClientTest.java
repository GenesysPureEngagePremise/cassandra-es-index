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

import com.genesyslab.webme.commons.index.requests.ElasticClientFactory;
import com.genesyslab.webme.commons.index.test.JestClientFactoryMock;
import com.genesyslab.webme.commons.index.test.JestClientMock;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Count;
import io.searchbox.core.CountResult;
import io.searchbox.core.Delete;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.Update;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.DeleteIndex;
import io.searchbox.indices.IndicesExists;
import io.searchbox.indices.Stats;
import io.searchbox.indices.aliases.AddAliasMapping;
import io.searchbox.indices.aliases.AliasMapping;
import io.searchbox.indices.aliases.GetAliases;
import io.searchbox.indices.aliases.ModifyAliases;

import java.io.IOException;
import java.util.Properties;

import static com.genesyslab.webme.commons.index.ElasticIndex.DOC_AS_UPSERT;
import static com.genesyslab.webme.commons.index.JsonUtils.getJsonObject;

/**
 * This class does not test much, it's mainly used to develop Jest requests against a local ES
 * <p>
 * Created by Jacques-Henri Berthemet on 06/07/2017.
 */
public class ElasticClientTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticClientTest.class);
  private static final boolean TESTING = true; //set this to false to use a local ES, will use mocked client else

  private final JestClient jclient = ElasticClientFactory.getJestClientFactory().getObject();
  private String typeName = "Contact";
  private String indexName = "ucs@";
  private String aliasName = "ucs";

  @BeforeClass
  public static void setup() {
    if (TESTING) {
      ElasticClientFactory.setJestClientFactory(JestClientFactoryMock.INSTANCE);
    }
  }

  @AfterClass
  public static void cleanup() {
    JestClientMock.clear();
    ElasticClientFactory.setJestClientFactory(null);
  }


  private <T extends JestResult> T executeSync(Action<T> request) {
    try {

      LOGGER.info("request: {}", request.toString());
      T res = jclient.execute(request);

      LOGGER.info("isSucceeded: {}", res.isSucceeded());
      LOGGER.info("getErrorMessage: {}", res.getErrorMessage());
      LOGGER.info("getResponseCode: {}", res.getResponseCode());
      LOGGER.info("Json: {}", res.getJsonString());

      return res;
    } catch (IOException e) {
      LOGGER.error("Failed to send sync ES request:{}", request, e);
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testIndicesExists() {
    JestClientMock.addResponse(new JestResult(new Gson()));

    JestResult indicesExistsResult = executeSync(new IndicesExists.Builder(indexName).build());
  }

  @Test
  public void testCreateIndex() {
    JestClientMock.addResponse(new JestResult(new Gson()));

    Properties indexProperties = new Properties();
    indexProperties.setProperty("analysis.analyzer.html_analyzer.char_filter", "html_strip");
    indexProperties.setProperty("analysis.analyzer.html_analyzer.filter", "lowercase");
    indexProperties.setProperty("analysis.analyzer.html_analyzer.tokenizer", "standard");
    indexProperties.setProperty("analysis.analyzer.html_analyzer.type", "custom");
    indexProperties.setProperty("analysis.tokenizer.email_tokenizer.type", "pattern");
    indexProperties.setProperty("analysis.tokenizer.email_tokenizer.pattern", "[^a-z0-9]");
    indexProperties.setProperty("analysis.analyzer.email_analyzer.filter", "lowercase");
    indexProperties.setProperty("analysis.analyzer.email_analyzer.tokenizer", "email_tokenizer");
    indexProperties.setProperty("analysis.analyzer.email_analyzer.type", "pattern");
    indexProperties.setProperty("index.analysis.analyzer.lowercase_analyzer.tokenizer", "keyword");
    indexProperties.setProperty("index.analysis.analyzer.lowercase_analyzer.filter", "lowercase");
    indexProperties.setProperty("index.analysis.analyzer.lowercase_analyzer.type", "custom");

    JestResult createIndexResult = executeSync(new CreateIndex.Builder(indexName).settings(indexProperties.toString()).build());
  }


  @Test
  public void testAliasMapping() {
    JestClientMock.addResponse(new JestResult(new Gson()));

    AliasMapping aliases = new AddAliasMapping.Builder(indexName, aliasName).build();
    JestResult addAliasResult = executeSync(new ModifyAliases.Builder(aliases).build());

    JestClientMock.addResponse(new JestResult(new Gson()));
    JestResult aliasesResponses = executeSync(new GetAliases.Builder().addIndex(aliasName).build());
    if (aliasesResponses.isSucceeded()) {
      getJsonObject(aliasesResponses, indexName, "aliases")
        .entrySet().forEach(elem -> {
        String indexNameToDelete = elem.getKey();
        CountResult count = executeSync(new Count.Builder().addIndex(indexNameToDelete).build());
        if (count.isSucceeded() && count.getCount().intValue() == 0) {
          executeSync(new DeleteIndex.Builder(indexNameToDelete).build());
        }
      });
    }
  }


  @Test
  public void testStats() {
    JestClientMock.addResponse(new JestResult(new Gson()));

    JestResult stats = executeSync(new Stats.Builder().addIndex(indexName).build());
    JsonElement count = getJsonObject(stats, "indices", indexName, "total", "docs").get("count");
  }

  @Test
  public void testSearch() {
    JestClientMock.addResponse(new SearchResult(new Gson()));

    int size = 1;
    String query = String.format("{\"size\":%d,\"query\":{\"query_string\":{\"query\":\"%s\"}}}", size, "user:*");

    Search searchRequest = new Search.Builder(query)
      .addIndex(aliasName)
      .addType(typeName)
      .build();

    SearchResult searchResponse = executeSync(searchRequest);
    JsonObject res = searchResponse.getJsonObject();

    LOGGER.info("Index {} search result: {}", typeName, getJsonObject(searchResponse, "hits").get("hits"));
  }

  @Test
  public void testPipeline() {
    String doc = "{\"key\":\"value\"}";
    String docId = "42";
    JestResult res =
      executeSync(new Index.Builder(doc).index(indexName).type(typeName).id(docId).setParameter("pipeline", typeName).build());
  }

  @Test
  public void testRetryRequest() {
    String doc = "{\"key\":\"value\"}";
    String docId = "42";
    Update.Builder update = new Update.Builder(String.format(DOC_AS_UPSERT, doc))
      .index(aliasName)
      .type(typeName)
      .id(docId)
      .setParameter("retry_on_conflict", 5);

    executeSync(update.build());
  }

  @Test
  public void testDeleteIndex() {
    Delete delete = new Delete.Builder("").index(aliasName).build();
    executeSync(delete);
  }
}
