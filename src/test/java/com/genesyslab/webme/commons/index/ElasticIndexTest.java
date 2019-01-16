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
import com.genesyslab.webme.commons.index.config.IndexConfig.Segment;
import com.genesyslab.webme.commons.index.requests.ElasticClientFactory;
import com.genesyslab.webme.commons.index.test.JestClientMock;

import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.utils.Pair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatcher;

import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Vincent Pirat 01/12/2016
 */
public class ElasticIndexTest {

  private static final String KEYSPACE_NAME = "testKeyspace";
  private static final String TABLE_NAME = "testTable";
  private static JestClientFactory jestClientFactory = mock(JestClientFactory.class);

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  private IndexConfig indexConfigMock = mock(IndexConfig.class);

  @BeforeClass
  public static void setupClient() {
    DatabaseDescriptor.clientInitialization(false);
    when(jestClientFactory.getObject()).thenReturn(new JestClientMock());
    ElasticClientFactory.setJestClientFactory(jestClientFactory);
  }

  @AfterClass
  public static void cleanup() {
    JestClientMock.clear();
    ElasticClientFactory.setJestClientFactory(null);
  }

  private static ArgumentMatcher<HttpClientConfig> hasNoCredentials() {
    return httpClientConfig -> httpClientConfig != null && httpClientConfig.getCredentialsProvider() == null;
  }

  private static ArgumentMatcher<HttpClientConfig> hasCredentials(final String userName, final String password) {
    return httpClientConfig -> {
      if (httpClientConfig == null) {
        return false;
      }
      if (httpClientConfig.getCredentialsProvider() == null) {
        return false;
      }
      Credentials credentials = httpClientConfig.getCredentialsProvider().getCredentials(AuthScope.ANY);
      if (credentials == null) {
        return false;
      }
      return credentials.getUserPrincipal().getName().equals(userName) && credentials.getPassword().equals(password);
    };
  }

  @Before
  public void setup() {
    when(indexConfigMock.isPerIndexType()).thenReturn(true); //default
    when(indexConfigMock.getIndexSegment()).thenReturn(Segment.OFF);
    when(indexConfigMock.isInsertOnly()).thenReturn(false);
    when(indexConfigMock.getIndexPurgePeriod()).thenReturn(60);
    when(indexConfigMock.getIndexManagerName()).thenReturn(IndexConfig.ES_INDEX_MANAGEMENT_DEF);
  }

  @After
  public void tearDown() {
    assertFalse(JestClientMock.hasMoreResponses());
  }

  @Test
  public void shouldSetDefaultNames() {
    when(indexConfigMock.getIndexSegment()).thenReturn(Segment.OFF);

    ElasticIndex indexUnderTest = new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());

    assertEquals("testkeyspace_testtable", indexUnderTest.indexManager.getAliasName());
    assertEquals("testTable", indexUnderTest.typeName);
    assertEquals("testkeyspace_testtable_index@", indexUnderTest.indexManager.getCurrentName());
  }

  @Test
  public void shouldSetCustomIndexName() {
    String date = "2016-11-18-10";
    when(indexConfigMock.getIndexSegment()).thenReturn(Segment.CUSTOM);
    when(indexConfigMock.getIndexSegmentName()).thenReturn(date);

    ElasticIndex indexUnderTest = new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());

    assertEquals("testkeyspace_testtable", indexUnderTest.indexManager.getAliasName());
    assertEquals("testTable", indexUnderTest.typeName);
    assertEquals("testkeyspace_testtable_index@" + date, indexUnderTest.indexManager.getCurrentName());
  }

  @Test
  public void shouldSetTimedIndexName() {

    SimpleDateFormat sf = new SimpleDateFormat("yyyy-MM");
    sf.setTimeZone(TimeZone.getTimeZone("UTC"));
    String date = sf.format(new Date());

    when(indexConfigMock.getIndexSegment()).thenReturn(Segment.MONTH);
    when(indexConfigMock.getIndexSegmentName()).thenReturn(date);

    ElasticIndex indexUnderTest = new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());

    assertEquals("testkeyspace_testtable", indexUnderTest.indexManager.getAliasName());
    assertEquals("testTable", indexUnderTest.typeName);
    assertEquals("testkeyspace_testtable_index@" + date, indexUnderTest.indexManager.getCurrentName());
  }

  // WCC-886
  @Test
  public void shouldAcceptIndexUpdate() {
    String indexUpdate = "#update#{\"read-consistency-level\":\"LOCAL_QUORUM\",\"insert-only\":\"false\",\"discard-nulls\":\"false\","
      + "\"async-search\":\"false\",\"async-write\":\"false\",\"json-serialized-fields\":\"AttributeValues,AttributeValuesDate\","
      + "\"json-flat-serialized-fields\":\"AllAttributes\",\"validate-queries\":\"true\","
      + "\"class_name\":\"com.genesyslab.webme.commons.index.EsSecondaryIndex\",\"segment\":\"CUSTOM\","
      + "\"segment-name\":\"9.9.999.99-FIXME-2017-01-19-13-08\",\"index-properties\":\"{\"index.refresh_interval\":\"1s\","
      + "\"script.disable_dynamic\":\"false\",\"analysis.analyzer.html_analyzer.char_filter\":\"html_strip\","
      + "\"analysis.analyzer.html_analyzer.type\":\"custom\",\"analysis.analyzer.html_analyzer.filter\":\"standard\","
      + "\"analysis.analyzer.html_analyzer.tokenizer\":\"standard\"}\",\"async-search\":\"false\",\"async-write\":\"false\","
      + "\"client-only\":\"false\",\"discard-nulls\":\"false\",\"insert-only\":\"false\",\"mapping-Interaction\":\"{  \"Interaction\": {"
      + "    \"dynamic\": \"true\",    \"date_detection\" : false,    \"numeric_detection\" : false,    \"dynamic_templates\": [      {"
      + "        \"template_EmailAddress\": {          \"path_match\": \"AllAttributes.EmailAddress\",          \"mapping\": {"
      + "            \"type\": \"string\",            \"index\": \"analyzed\",            \"analyzer\": \"keyword_email\"          }"
      + "        }      },      {        \"template_All\": {          \"path_match\": \"AllAttributes.*\",          \"mapping\": {"
      + "            \"type\": \"string\",            \"index\": \"analyzed\",            \"analyzer\": \"standard\"          }        }"
      + "      }    ],    \"properties\": {      \"Id\": {        \"type\": \"string\",        \"analyzer\": \"keyword\"      },"
      + "      \"TenantId\": {        \"type\": \"long\",        \"analyzer\": \"keyword\"      },      \"Segment\": {"
      + "        \"type\": \"string\",        \"analyzer\": \"keyword\"      },      \"EntityTypeId\": {        \"type\": \"long\","
      + "        \"analyzer\": \"keyword\"      },      \"MediaTypeId\": {        \"type\": \"string\",        \"analyzer\": \"keyword\""
      + "      },      \"TypeId\": {        \"type\": \"string\",        \"analyzer\": \"keyword\"      },      \"SubtypeId\": {"
      + "        \"type\": \"string\",        \"analyzer\": \"keyword\"      },      \"Status\": {        \"type\": \"long\","
      + "        \"analyzer\": \"keyword\"      },      \"CreatorAppId\": {        \"type\": \"long\",        \"analyzer\": \"keyword\""
      + "      },      \"ThreadId\": {        \"type\": \"string\",        \"analyzer\": \"keyword\"      },      \"ContactId\": {"
      + "        \"type\": \"string\",        \"analyzer\": \"keyword\"      },      \"Owners\": {        \"type\": \"long\","
      + "        \"analyzer\": \"keyword\"      },      \"StartDate\": {        \"type\": \"date\","
      + "        \"format\": \"yyyy-MM-dd''T''HH:mm:ss.SSSZZ\"      },      \"EndDate\": {        \"type\": \"date\","
      + "        \"format\": \"yyyy-MM-dd''T''HH:mm:ss.SSSZZ\"      },      \"ModifiedDate\": {        \"type\": \"date\","
      + "        \"format\": \"yyyy-MM-dd''T''HH:mm:ss.SSSZZ\"      },      \"ExternalId\": {        \"type\": \"string\","
      + "        \"index\": \"analyzed\",        \"analyzer\": \"keyword\"      },      \"ParentId\": {        \"type\": \"string\","
      + "        \"analyzer\": \"keyword\"      },      \"CanBeParent\": {        \"type\": \"string\",        \"analyzer\": \"keyword\""
      + "      },      \"OwnerId\": {        \"type\": \"long\",        \"analyzer\": \"keyword\"      },       \"toto\": {"
      + "        \"type\": \"long\",        \"analyzer\": \"keyword\"      },       \"tutu\": {        \"type\": \"long\","
      + "        \"analyzer\": \"keyword\"      },      \"Content\": {        \"type\": \"string\",        \"enabled\": false      },"
      + "      \"ContentIds\": {        \"type\": \"object\",        \"enabled\": false      },      \"ContentSize\": {"
      + "        \"type\": \"long\",        \"analyzer\": \"keyword\"      },      \"MimeType\": {        \"type\": \"string\","
      + "        \"analyzer\": \"standard\"      },      \"StructuredText\": {        \"type\": \"string\","
      + "        \"analyzer\": \"html_analyzer\"      },      \"Text\": {        \"type\": \"string\",        \"analyzer\": \"standard\""
      + "      },      \"AllAttributes\": {        \"type\": \"object\",        \"analyzer\": \"standard\"      },      \"Attributes\": {"
      + "        \"type\": \"object\",        \"analyzer\": \"standard\",        \"properties\" : {          \"TConnectionId\": {"
      + "            \"type\": \"string\",            \"analyzer\": \"keyword\"          },          \"SentDate\": {"
      + "            \"type\": \"date\",            \"format\": \"yyyy-MM-dd''T''HH:mm:ss.SSSZZ\"          }        }      },"
      + "      \"Participant\": {        \"type\": \"string\",        \"analyzer\": \"keyword\"      },      \"Attachments\": {"
      + "        \"type\": \"string\",        \"analyzer\": \"standard\"      }    }  }}\"}#";

    when(indexConfigMock.isValidateQuery()).thenReturn(true);

    ElasticIndex indexUnderTest = new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());

    indexUnderTest.validate(indexUpdate);
  }

  @Test
  public void shouldSkipValidation() {
    when(indexConfigMock.isValidateQuery()).thenReturn(false);
    ElasticIndex indexUnderTest = new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());
    indexUnderTest.validate("Just accept anything, skip validation");
  }

  //WCC-886
  @Test
  public void shouldValidateQueries() {
    JestResult jestResult = mock(JestResult.class);
    when(jestResult.isSucceeded()).thenReturn(true);
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("valid", "true");
    when(jestResult.getJsonObject()).thenReturn(jsonObject);

    when(indexConfigMock.isValidateQuery()).thenReturn(true);

    ElasticIndex indexUnderTest = new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());

    JestClientMock.addResponse(jestResult);
    indexUnderTest.validate("Id:123456");

    JestClientMock.addResponse(jestResult);
    indexUnderTest.validate("{\"Id\":123456}");

    JestClientMock.addResponse(jestResult);
    indexUnderTest.validate("#accept options between hashes#Id:123456");

    indexUnderTest.validate("# accept # anything # between hashes #");
  }

  // WCC-886
  @Test
  public void validateQueryShouldFail() {
    expectedException.expect(InvalidRequestException.class);
    expectedException.expectMessage("Query starts with '#', but second '#' is missing");

    when(indexConfigMock.isValidateQuery()).thenReturn(true);
    ElasticIndex indexUnderTest = new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());

    indexUnderTest.validate("#missing ending hash");
  }

  //WCC-886
  @Test
  public void validateQueryShouldFailIsSucceededFalse() {
    expectedException.expect(InvalidRequestException.class);
    expectedException.expectMessage("Jest result error message");

    JestResult jestResult = mock(JestResult.class);
    when(jestResult.isSucceeded()).thenReturn(false);
    when(jestResult.getErrorMessage()).thenReturn("Jest result error message");

    when(indexConfigMock.isValidateQuery()).thenReturn(true);
    ElasticIndex indexUnderTest = new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());

    JestClientMock.addResponse(jestResult);
    indexUnderTest.validate("Invalid query");
  }

  //WCC-886
  @Test
  public void validateQueryShouldFailValidIsFalse() {
    expectedException.expect(InvalidRequestException.class);
    expectedException.expectMessage("Query is invalid");
    JestResult jestResult = mock(JestResult.class);
    when(jestResult.isSucceeded()).thenReturn(true);
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("valid", "false");
    jsonObject.addProperty("explanations", "Query is invalid");
    when(jestResult.getJsonObject()).thenReturn(jsonObject);

    when(indexConfigMock.isValidateQuery()).thenReturn(true);
    ElasticIndex indexUnderTest = new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());

    JestClientMock.addResponse(jestResult);
    indexUnderTest.validate("Id:123456");
  }

  @Test
  public void testJsonSerializedFields() throws IOException {
    ElasticIndex indexUnderTest = new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());
    when(indexConfigMock.getJsonSerializedFields()).thenReturn(Sets.newHashSet("JsonCol"));
    indexUnderTest.updateIndexConfigOptions();

    List<Pair<String, String>> pks = singletonList(Pair.create("Id", "42"));
    List<CellElement> elems =
      asList(CellElement.create("Value", "hello", null), CellElement.create("JsonCol", "{\"key\":\"value\"}", null));
    indexUnderTest.index(pks, elems, 7L, true);

    String req = JestClientMock.lastRequest.toString() + JestClientMock.lastRequest.getData(new Gson());
    assertThat(req, startsWith(
      "Update{uri=testkeyspace_testtable_index%40/testTable/42/_update?retry_on_conflict=0, method=POST}{\"doc\":{\"Id\":\"42\",\"Value\":\"hello\",\"JsonCol\":{\"key\":\"value\"},\"IndexationDate\":"));
    assertThat(req, endsWith("_cassandraTtl\":7},\"doc_as_upsert\":true}")); //WCC-1160
  }

  @Test
  public void testCorrectDrop() {
    ElasticIndex indexUnderTest = new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());
    indexUnderTest.drop();

    assertThat(JestClientMock.lastRequest.toString(), is("Delete{uri=testkeyspace_testtable_index%40, method=DELETE}"));
  }

  @Test
  public void shouldIgnoreCredentialsWhenNotProvided() {
    clearInvocations(jestClientFactory);

    System.setProperty("ESCREDENTIALS", "");
    ElasticIndex.readEsCredentials();

    new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());

    verify(jestClientFactory).setHttpClientConfig(argThat(hasNoCredentials()));
    verify(jestClientFactory).getObject();
    verifyNoMoreInteractions(jestClientFactory);
  }

  @Test
  public void shouldUseCredentialsReadFromEnv() {
    clearInvocations(jestClientFactory);

    String userName = randomAlphabetic(8);
    String password = randomAlphabetic(12);
    System.setProperty("ESCREDENTIALS", userName + ":" + password);

    ElasticIndex.readEsCredentials();

    new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());

    verify(jestClientFactory).setHttpClientConfig(argThat(hasCredentials(userName, password)));
    verify(jestClientFactory).getObject();
    verifyNoMoreInteractions(jestClientFactory);
  }

  @Test
  public void shouldIgnoreCredentialsWhenInvalid() {
    clearInvocations(jestClientFactory);

    System.setProperty("ESCREDENTIALS", randomAlphabetic(10));
    ElasticIndex.readEsCredentials();

    new ElasticIndex(indexConfigMock, KEYSPACE_NAME, TABLE_NAME, singletonList("Id"), emptyList());

    verify(jestClientFactory).setHttpClientConfig(argThat(hasNoCredentials()));
    verify(jestClientFactory).getObject();
    verifyNoMoreInteractions(jestClientFactory);
  }

}
