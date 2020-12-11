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
import com.genesyslab.webme.commons.index.monitor.EsJmxBridge;
import com.genesyslab.webme.commons.index.requests.ElasticClientFactory;
import com.genesyslab.webme.commons.index.requests.GenericRequest;
import com.genesyslab.webme.commons.index.requests.ResponseHandler;
import com.genesyslab.webme.commons.index.requests.UpdatePipeline;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.exceptions.CassandraException;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.Pair;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.cluster.Health;
import io.searchbox.core.Count;
import io.searchbox.core.CountResult;
import io.searchbox.core.Delete;
import io.searchbox.core.DeleteByQuery;
import io.searchbox.core.DocumentResult;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.Update;
import io.searchbox.core.Validate;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.DeleteIndex;
import io.searchbox.indices.Flush;
import io.searchbox.indices.IndicesExists;
import io.searchbox.indices.aliases.AddAliasMapping;
import io.searchbox.indices.aliases.AliasMapping;
import io.searchbox.indices.aliases.GetAliases;
import io.searchbox.indices.aliases.ModifyAliases;
import io.searchbox.indices.mapping.GetMapping;
import io.searchbox.indices.mapping.PutMapping;
import io.searchbox.indices.settings.UpdateSettings;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.genesyslab.webme.commons.index.JsonUtils.dotedToStructured;
import static io.searchbox.params.Parameters.EXPLAIN;
import static io.searchbox.params.Parameters.RETRY_ON_CONFLICT;
import static org.json.simple.JSONValue.escape;

/**
 * ES client based on Jest
 * <p>
 * Created by Jacques-Henri Berthemet on 05/07/2017.
 */
public class ElasticIndex implements IndexInterface {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticIndex.class);

  // ES constants
  private static final String ES_HITS = "hits";
  private static final String ES_SOURCE = "_source";
  private static final String ES_ID = "_id";
  private static final String ES_PIPELINE = "pipeline";
  private static final String ES_LOCALHOST = "http://localhost:";
  private static final String ES_CREDENTIALS = "ESCREDENTIALS";

  // Can be useful to restart a Cassandra node with bad JSON
  private static final boolean SKIP_BAD_JSON = Boolean.getBoolean(IndexConfig.ES_CONFIG_PREFIX + "skip-bad-json");
  private static final boolean ENABLE_INDEXATION_DATE = !Boolean.getBoolean(IndexConfig.ES_CONFIG_PREFIX + "disable-index-date");
  private static final long DISCOVERY_FREQ = Long.getLong(IndexConfig.ES_CONFIG_PREFIX + "discovery-frequency", 5);

  // Special fields
  private static final String TTL_FIELD = "_cassandraTtl";
  private static final String INDEXATION_DATE = "IndexationDate";

  // Wrapped queries
  private static final String QUERY_WRAPPER_WITH_SIZE = "{\"size\":%d,\"query\":{\"query_string\":{\"query\":\"%s\"}}}";
  private static final String QUERY_WRAPPER = "{\"query\":%s}";
  private static final String QUERY_WRAPPER_WITH_QUOTES = "{\"query\":{\"query_string\":{\"query\":\"%s\"}}}";
  static final String DOC_AS_UPSERT = "{\"doc\":%s,\"doc_as_upsert\":true}";
  private static final String MATCH_ALL = "*";
  private static final String MATCH_LTE = "{\"conflicts\":\"proceed\",\"query\":{\"range\":{\"" + TTL_FIELD + "\":{\"lte\":%d}}}}";
  private static final String JSON_PREFIX = "{";

  private static EsJmxBridge jmxMon;

  private static String esUserName;
  private static String esPassword;

  static {
    readEsCredentials();
  }


  static void readEsCredentials() {
    esUserName = null;
    esPassword = null;

    String credentialsOrigin = "";

    String credentials = System.getenv(ES_CREDENTIALS);
    if (credentials == null) {
      credentials = System.getProperty(ES_CREDENTIALS);
      if (credentials != null) {
        credentialsOrigin = " from system properties";
      }
    }

    if (credentials != null) {
      int colon = credentials.indexOf(':');
      if (colon > 0) {
        esUserName = credentials.substring(0, colon);
        esPassword = credentials.substring(colon + 1);
        LOGGER.info("Elasticsearch credentials provided{}", credentialsOrigin);
      } else {
        LOGGER.info("Elasticsearch credentials{} are incorrect, missing colon", credentialsOrigin);
      }
    }
  }

  final String typeName;
  final IndexManager indexManager;

  private final JestClient client;
  private final IndexConfig indexConfig;
  private final JsonFactory jsonFactory = new JsonFactory();
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicBoolean newIndex = new AtomicBoolean();
  private final String pkIncludePattern;
  private final List<String> partitionKeysNames;
  private final List<String> clusteringColumnsNames;
  private final boolean hasClusteringColumns;

  private boolean usePipeline;
  private int ttlShift;
  private boolean isConcurrentLock;
  private Set<String> jsonFlatSerializedFields;
  private Set<String> jsonSerializedFields;
  private int maxResults;
  private boolean isValidateQuery;
  private boolean isAsyncWrite;
  private boolean insertOnly;
  private int httpPort;
  private boolean isV6 = true; //v6 or less

  ElasticIndex(@Nonnull IndexConfig indexConfig, @Nonnull String indexName, @Nonnull String tableName,
    @Nonnull List<String> partitionKeysNames, @Nonnull List<String> clusteringColumnsNames) throws ConfigurationException {
    this.indexConfig = indexConfig;
    this.partitionKeysNames = partitionKeysNames;
    this.clusteringColumnsNames = clusteringColumnsNames;
    this.typeName = tableName;

    this.indexManager = getIndexManager(indexConfig, indexName);
    updateIndexConfigOptions();

    String unicastHosts = indexConfig.getUnicastHosts();
    List<String> esUrls = new ArrayList<>();

    String defaultSchemeForDiscoveredNodes = "http";
    for (String host : (unicastHosts == null ? ES_LOCALHOST + httpPort : unicastHosts).split(",")) {
      if (host.startsWith("https")) {
        defaultSchemeForDiscoveredNodes = "https";
      }
      host = host.startsWith("http") ? host : "http://" + host;
      host = host.substring("http://".length()).contains(":") ? host : host + ":" + httpPort; // also works for https://
      esUrls.add(host);
    }

    int timeout = (int) Math.max(DatabaseDescriptor.getWriteRpcTimeout(), DatabaseDescriptor.getReadRpcTimeout());
    int maxCon = DatabaseDescriptor.getConcurrentWriters() + DatabaseDescriptor.getConcurrentReaders();

    LOGGER.info("Request timeout: {}ms, max connections: {}, discovery: {}m", timeout, maxCon, DISCOVERY_FREQ);

    HttpClientConfig.Builder httpConfigBuilder = new HttpClientConfig.Builder(esUrls)
      .multiThreaded(true)
      .discoveryEnabled(true)
      .discoveryFrequency(DISCOVERY_FREQ, TimeUnit.MINUTES)
      .defaultMaxTotalConnectionPerRoute(indexConfig.getMaxTotalConnectionPerRoute())
      .maxTotalConnection(maxCon)
      .readTimeout(timeout); // ms

    if (esUserName != null) {
      httpConfigBuilder.defaultCredentials(esUserName, esPassword);
    }

    if (Boolean.parseBoolean(System.getProperty("genesys-es-trustall", "true"))) {
      try {
        TrustStrategy trustAll = (x509Certificates, authenticationType) -> true;
        SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, trustAll).build();
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        SchemeIOSessionStrategy httpsIOSessionStrategy = new SSLIOSessionStrategy(sslContext, NoopHostnameVerifier.INSTANCE);

        httpConfigBuilder
          .defaultSchemeForDiscoveredNodes(defaultSchemeForDiscoveredNodes)
          .sslSocketFactory(sslSocketFactory)
          .httpsIOSessionStrategy(httpsIOSessionStrategy);

      } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
        LOGGER.warn("While configuring TLS, ", e);
      }
    }

    JestClientFactory factory = ElasticClientFactory.getJestClientFactory();
    factory.setHttpClientConfig(httpConfigBuilder.build());

    // Jest should be as good as ES REST client:
    // https://www.elastic.co/blog/benchmarking-rest-client-transport-client
    this.client = factory.getObject();

    StringBuilder include = new StringBuilder();
    Iterator<String> pkIterator = partitionKeysNames.iterator();
    while (pkIterator.hasNext()) {
      include.append(pkIterator.next());
      if (pkIterator.hasNext()) {
        include.append("|");
      }
    }
    Iterator<String> clkIterator = clusteringColumnsNames.iterator();
    while (clkIterator.hasNext()) {
      include.append(clkIterator.next());
      if (clkIterator.hasNext()) {
        include.append("|");
      }
    }

    this.pkIncludePattern = include.toString();
    this.hasClusteringColumns = !clusteringColumnsNames.isEmpty();
  }

  private IndexManager getIndexManager(@Nonnull IndexConfig indexConfig, String indexName) {
    IndexManager result;
    String className = indexConfig.getIndexManagerName();
    try {
      Class<?> clazz = Class.forName(className);
      Constructor<?> ctor = clazz.getConstructor(getClass(), IndexConfig.class, String.class);
      result = (IndexManager) ctor.newInstance(this, indexConfig, indexName);
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException
      | IllegalArgumentException | InvocationTargetException e) {
      String msg = "Index management " + className + " initialization failed";
      LOGGER.error(msg, e);
      throw new ConfigurationException(msg, e);
    }
    return result;
  }

  @Override
  public void init() throws ConfigurationException {
    try {
      if (jmxMon == null) {
        jmxMon = new EsJmxBridge(client);
      } else {
        LOGGER.debug("ES JMX bridge already registered");
      }
    } catch (Exception e) {
      LOGGER.error("Can't initialize the ES JMX bridge", e);
      if (!EsSecondaryIndex.START_WITH_FAILED_INDEX) {
        throw new ConfigurationException("Can't initialize the ES JMX bridge", e);
      }
    }

    LOGGER.info("ElasticIndex '{}' type '{}' initialization", indexManager.getAliasName(), typeName);

    // We now wait for the yellow (or green) status
    JestResult res = execute(new Health.Builder().waitForStatus(Health.Status.YELLOW).build()).waitForSuccess();
    LOGGER.debug("Got cluster status: {}", res.getJsonString());

    JsonObject version = execute(new GenericRequest("GET", "/", null)).waitForSuccess().getJsonObject().getAsJsonObject("version");
    String number = version.get("number").getAsString();
    LOGGER.info("Connected to Elasticsearch version {}", number);
    isV6 = Integer.parseInt(number.substring(0, 1)) < 7;

    setupIndex(indexManager.getCurrentName()); // Will create the ES index if needed

    LOGGER.debug("ElasticIndex '{}/{}' initialized, pipeline:{}", indexManager.getAliasName(), indexManager.getCurrentName(), usePipeline);
  }

  /**
   * Setup the index, either create new one or update existing
   *
   * @param indexName indexName to create
   * @throws ConfigurationException if create index failed
   */
  void setupIndex(String indexName) throws ConfigurationException {
    JsonObject indexProperties = indexConfig.getProperties();
    LOGGER.debug("Configuring {}/{} with settings:{}", indexManager.getAliasName(), indexName, indexProperties);

    indexProperties = JsonUtils.filterKeys(indexProperties, IndexConfig.KNOWN_LEGACY_SETTINGS);
    indexProperties = JsonUtils.filterKeys(indexProperties, IndexConfig.SETTINGS_TO_SKIP);
    indexProperties.entrySet().removeIf(elem -> elem.getKey().endsWith(IndexConfig.ES_UNICAST_HOSTS));

    if (
      indexProperties.get(IndexConfig.ES_TRANSLOG) == null) { // https://intranet.genesys.com/pages/viewpage.action?pageId=63998861
      indexProperties.addProperty(IndexConfig.ES_TRANSLOG, IndexConfig.ES_TRANSLOG_ASYNC);
    }

    //ES7 don't support dotted props anymore and requires them in the settings object
    if (!isV6 && !indexProperties.has("settings")) {
      LOGGER.warn("Received dotted index properties, converting to structured json compatible with ES v7");
      JsonObject settings = dotedToStructured(indexProperties);
      indexProperties = new JsonObject();
      indexProperties.add("settings", settings);
    }

    boolean indexExists = execute(new IndicesExists.Builder(indexName).build()).waitForResult().isSucceeded();
    if (indexExists) {
      LOGGER.warn("Index '{}' already exists, updating.", indexName);
      setupTypeMapping(indexName);
      setupPipelines();

      if (indexProperties.size() == 0) {
        LOGGER.debug("Index '{}' has no custom setting to apply", indexName);
        return;
      }

      JsonObject updatableProperties = JsonUtils.filter(indexProperties, IndexConfig.UPDATABLE_SETTINGS::contains);

      if (updatableProperties.size() == 0) {
        LOGGER.debug("No settings to update");
      } else {
        LOGGER.info("Applying updatable settings from cfg {}", updatableProperties);
        JestResult res = execute(new UpdateSettings.Builder(updatableProperties).addIndex(indexName).build()).waitForSuccess();
        LOGGER.info("Index settings update result is: {}", res.isSucceeded());
      }
    } else {
      LOGGER.warn("Index '{}' does not exist, creating...", indexName);

      CreateIndex.Builder createIndex = new CreateIndex.Builder(indexName);
      createIndex.settings(indexProperties.toString());

      JestResult createIndexResult = execute(createIndex.build()).waitForResult();
      boolean success = createIndexResult.isSucceeded();
      LOGGER.warn("Index creation result is: {}", success);

      if (success) {
        putAlias(indexName, indexManager.getAliasName());
        setupTypeMapping(indexName);
        setupPipelines();

        newIndex.set(true); // automatic rebuild support
      } else {
        if (execute(new IndicesExists.Builder(indexName).build()).waitForResult().isSucceeded()) {
          LOGGER.warn("Creation of index '{}' failed, but it exists now, it was created and configured by another node, proceeding...",
            indexName);
        } else {
          LOGGER.error("Failed to create the index '{}' {}", indexName, createIndexResult.getJsonString());
          throw new ConfigurationException(createIndexResult.getErrorMessage());
        }
      }
    }
  }

  /**
   * Create pipelines<br>
   * Instead mapping transform we can use pipeline:
   * https://www.elastic.co/guide/en/elasticsearch/reference/5.0/breaking_50_mapping_changes.html#_source_transform_removed
   * We can define a pipeline for every type, and when we make insert we will the pipeline if the
   * pipeline is defined for this type
   */
  private void setupPipelines() {
    indexConfig.getPipelines().stream().filter(StringUtils::isNotBlank) // Check null or empty
      .filter(type -> StringUtils.isNotBlank(indexConfig.getPipeline(type))) // Check pipeline definition exists
      .forEach(type -> {
        execute(new UpdatePipeline.Builder(type, indexConfig.getPipeline(type)).build()).waitForSuccess();
        LOGGER.debug("Pipeline created for '{}'", type);
      });
  }

  /**
   * Update the type mapping of an existing index
   */
  private void setupTypeMapping(String indexName) throws ConfigurationException {
    String mapping = indexConfig.getTypeMapping(typeName);

    if (StringUtils.isNotBlank(mapping)) {
      LOGGER.debug("Updating type mapping for '{}' to:\n\t{}", typeName, mapping);
      putMapping(indexName, mapping);
    }
  }

  @Override
  public SearchResult putMapping(String index, String source) {
    // We put the new getMapping on current index, not the alias
    PutMapping.Builder put = new PutMapping.Builder(index, typeName, source);
    if (!isV6) {
      put.setParameter("include_type_name", "true");
    }
    JestResult result = execute(put.build()).waitForSuccess();
    return new SearchResult(new ArrayList<>(), result.getJsonObject());
  }

  public SearchResult getMapping(String index) {
    JestResult result = execute(new GetMapping.Builder().addIndex(index).build()).waitForResult();
    return new SearchResult(new ArrayList<>(), result.getJsonObject());
  }

  @Override
  public void index(@Nonnull List<Pair<String, String>> partitionKeys, @Nonnull List<CellElement> elements, long expirationTime,
    boolean isInsert) throws IOException {
    if (isConcurrentLock) {
      // lock on intern representation of type+PK, for example: "Interaction[(Id,0001HZO1Qq0haiGs)]"
      // This prevents concurrent upserts on the same doc from the same node
      synchronized ((typeName + partitionKeys).intern()) {
        indexInternal(partitionKeys, elements, expirationTime);
      }
    } else {
      indexInternal(partitionKeys, elements, expirationTime);
    }
  }

  private void indexInternal(List<Pair<String, String>> partitionKeys, List<CellElement> elements, long expirationTime)
    throws IOException {

    Map<String, List<CellElement>> groupedMap = group(partitionKeys, elements);

    for (Map.Entry<String, List<CellElement>> entry : groupedMap.entrySet()) {
      update(partitionKeys, entry.getKey(), entry.getValue(), expirationTime);
    }
  }

  private void update(List<Pair<String, String>> partitionKeys, String docId, List<CellElement> elements, long expirationTime)
    throws IOException {

    StringWriter stringWriter = new StringWriter();

    try (JsonGenerator builder = jsonFactory.createJsonGenerator(stringWriter)) {
      builder.writeStartObject();

      for (Pair<String, String> pk : partitionKeys) {
        builder.writeStringField(pk.left, pk.right);
      }

      boolean clusteringKeysSet = false;

      Map<CellElement, Map<String, String>> collections = null;

      // Fill simple fields and map complex types
      for (CellElement element : elements) {

        if (element.clusteringKeys != null) {
          if (!clusteringKeysSet) { // Insert clustering keys if not already done
            for (Pair<String, String> key : element.clusteringKeys) {
              builder.writeStringField(key.left, key.right);
            }

            clusteringKeysSet = true;
          }
        }

        if (element.isCollection() && element.collectionValue != null) {
          if (collections == null) {
            collections = new HashMap<>();
          }

          collections.computeIfAbsent(element, k -> new HashMap<>()).put(element.collectionValue.name, element.collectionValue.value);

        } else if (element.value != null) {
          try {
            if (jsonFlatSerializedFields.contains(element.name)) {
              String flattenedJson = JsonUtils.flatten(element.value);
              builder.writeFieldName(element.name);
              builder.writeRawValue(flattenedJson);
            } else if (jsonSerializedFields.contains(element.name)) {
              builder.writeFieldName(element.name);
              builder.writeRawValue(element.value);
            } else { // Simple field
              builder.writeStringField(element.name, element.value);
            }
          } catch (IOException ex) {
            if (SKIP_BAD_JSON) {
              LOGGER.warn("Skipped bad json for field {} of document {}", element.name, docId, ex);
            } else {
              throw ex;
            }
          }
        } else { // null value
          builder.writeNullField(element.name);
        }
      }

      if (collections != null) {
        // Fill the collections now that they are sorted
        for (Map.Entry<CellElement, Map<String, String>> collection : collections.entrySet()) {
          CellElement element = collection.getKey();

          if (element.collectionValue != null) {
            switch (element.collectionValue.type) {
              case JSON:
                builder.writeObjectFieldStart(element.name);

                for (Map.Entry<String, String> en : collection.getValue().entrySet()) {
                  String value = en.getValue();
                  if (value == null) {
                    builder.writeNullField(en.getKey());
                  } else {
                    builder.writeFieldName(en.getKey());
                    builder.writeRawValue(value);
                  }
                }
                builder.writeEndObject();
                break;

              case MAP:
                builder.writeObjectFieldStart(element.name);
                for (Map.Entry<String, String> entry : collection.getValue().entrySet()) {
                  builder.writeStringField(entry.getKey(), entry.getValue());
                }
                builder.writeEndObject();
                break;

              case LIST:
              case SET:
                builder.writeArrayFieldStart(element.name);
                for (String value : collection.getValue().keySet()) {
                  builder.writeString(value);
                }
                builder.writeEndArray();
                break;

              default:
                // There is no other CollectionType
            }
          }
        }
      }

      if (ENABLE_INDEXATION_DATE) {
        builder.writeStringField(INDEXATION_DATE, JsonUtils.getIso8601Date(new Date()));
      }

      if (indexManager.isTTLFieldRequired()) {
        builder.writeNumberField(TTL_FIELD, expirationTime);
      }

      builder.writeEndObject();
      builder.close(); // calling close() early because we want the output now
      String jsonDoc = stringWriter.toString();

      if (EsSecondaryIndex.DEBUG_SHOW_VALUES) {
        String operation = (insertOnly || usePipeline) ? "insert" : "upsert";
        LOGGER.debug("Document {} index {} {} with content {}", typeName, operation, docId, jsonDoc);
      }

      String currentName = indexManager.getCurrentName();
      ResponseHandler<DocumentResult> handler;
      if (insertOnly || usePipeline) {
        Index.Builder indexRequest = new Index.Builder(jsonDoc).index(currentName).type(typeName).id(docId);

        if (usePipeline) { // https://www.elastic.co/guide/en/elasticsearch/reference/5.5/ingest.html
          indexRequest.setParameter(ES_PIPELINE, typeName);
        }
        handler = execute(indexRequest.build());

      } else {
        // Pipelines can only be used with index or bulk
        Update.Builder update = new Update.Builder(String.format(DOC_AS_UPSERT, jsonDoc))
          .index(currentName)
          .type(typeName)
          .id(docId);

        if (indexConfig.getRetryOnConflict() > -1) {
          update.setParameter(RETRY_ON_CONFLICT, indexConfig.getRetryOnConflict());
        }

        handler = execute(update.build());
      }

      if (!isAsyncWrite) {
        handler.waitForSuccess(); // Will block until response anc ensure result is a success
      }
    }
  }

  /**
   * Group all CellElement according to their clustering keys
   *
   * @param partitionKeys not null
   * @param elements      not null
   * @return a grouped map
   */
  private Map<String, List<CellElement>> group(List<Pair<String, String>> partitionKeys, List<CellElement> elements) {
    Map<String, List<CellElement>> sortedCells = new HashMap<>();

    for (CellElement element : elements) {
      String docId = CStarUtils.toEsId(partitionKeys, element.clusteringKeys);
      sortedCells.computeIfAbsent(docId, k -> new ArrayList<>()).add(element);
    }

    return sortedCells;
  }

  @Override
  public void delete(@Nonnull List<Pair<String, String>> partitionKeys) {
    String docId = CStarUtils.toEsId(partitionKeys, null);
    String currentName = indexManager.getCurrentName();
    ResponseHandler<DocumentResult> handler = execute(new Delete.Builder(docId).index(currentName).type(typeName).build());
    if (!isAsyncWrite) {
      handler.waitForStatus(200, 404, 204); // Blocks until response. Does not ensure result is a success.
    }
  }

  @Override
  public Object flush() {
    return execute(new Flush.Builder().addIndex(indexManager.getCurrentName()).force(true).build()).waitForSuccess();
  }

  @Override
  @Nonnull
  public SearchResult search(@Nonnull QueryMetaData queryMetaData) {
    String queryString = queryMetaData.query;

    LOGGER.trace("Index {} search with query {}", typeName, queryString);

    if (!queryString.startsWith(JSON_PREFIX)) {
      queryString = String.format(QUERY_WRAPPER_WITH_SIZE, maxResults, escape(queryString));
    }

    Search.Builder builder = new Search.Builder(queryString).addIndex(indexManager.getAliasName()).addType(typeName);

    io.searchbox.core.SearchResult searchResponse;
    try {
      Search searchRequest = queryMetaData.loadSource() ? builder.build() : builder.addSourceIncludePattern(pkIncludePattern).build();
      searchResponse = execute(searchRequest).waitForSuccess();
    } catch (CassandraException e) {
      throw new InvalidRequestException(e.getMessage());
    }

    LOGGER.trace("Index {} search result: {}", typeName, searchResponse);

    final List<SearchResultRow> idList = new ArrayList<>();

    JsonElement hits = JsonUtils.getJsonObject(searchResponse, ES_HITS).get(ES_HITS);
    if (hits != null) {
      List<String> primaryKeys;

      if (hasClusteringColumns) {
        primaryKeys = new ArrayList<>(partitionKeysNames.size() + clusteringColumnsNames.size());
        primaryKeys.addAll(partitionKeysNames);
        primaryKeys.addAll(clusteringColumnsNames);
      } else {
        primaryKeys = partitionKeysNames;
      }

      int pkSize = primaryKeys.size();

      hits.getAsJsonArray().forEach(result -> {
        String[] primaryKey = new String[pkSize];

        int keyNb = 0;

        for (String keyName : primaryKeys) {
          String value = JsonUtils.getString(result, ES_SOURCE, keyName);

          if (value == null) {
            LOGGER.warn("Missing pk {} from ES results, skipping hit:{}", keyName, JsonUtils.getString(result, ES_ID));
            continue;
          } else {
            primaryKey[keyNb] = value;
          }
          keyNb++;
        }

        if (keyNb == pkSize) { // Will only be false if we skipped a hit, see above warning
          idList.add(new SearchResultRow(primaryKey, result.getAsJsonObject()));
        }

      });
    }

    // Remove the content of {"hits":{"hits": (big values) } }
    JsonObject metadata = JsonUtils.filterPath(searchResponse.getJsonObject(), ES_HITS, ES_HITS);
    return new SearchResult(idList, metadata);
  }

  private String extractQuery(String query) {
    try {
      return mapper.readTree(query).get("query").toString();
    } catch (Exception e) {
      LOGGER.trace("Could not extract query node from '{}' for Index {}", query, indexManager.getAliasName());
    }
    return query;
  }

  @Override
  public void validate(@Nonnull String query) throws InvalidRequestException {
    if (!isValidateQuery) {
      return;
    }
    LOGGER.trace("Validating query {}", query);

    String esQuery = query;

    // Ignore index management queries like #update# .... #
    if (query.startsWith("#")) {
      if (query.endsWith("#")) { //WCC-886
        return;
      } else {
        int optionEnd = query.indexOf("#", 1);
        if (optionEnd < 0) {
          throw new InvalidRequestException("Query starts with '#', but second '#' is missing");
        }
        esQuery = query.substring(optionEnd + 1);
      }
    }

    String formattedQuery;
    if (esQuery.startsWith(JSON_PREFIX)) {
      formattedQuery = String.format(QUERY_WRAPPER, extractQuery(esQuery)); // WCC-876
    } else {
      formattedQuery = String.format(QUERY_WRAPPER_WITH_QUOTES, esQuery);
    }

    LOGGER.trace("Validating query {}", formattedQuery);
    try {
      Validate.Builder validateBuilder = new Validate.Builder(formattedQuery);
      validateBuilder.setParameter(EXPLAIN, String.valueOf(true));
      JestResult res = execute(validateBuilder.build()).waitForResult();
      if (!res.isSucceeded()) {
        LOGGER.info("Query {} is invalid", formattedQuery);
        throw new InvalidRequestException(res.getErrorMessage());
      } else {
        String valid = res.getJsonObject().get("valid").getAsString();
        if (Boolean.parseBoolean(valid)) {
          LOGGER.trace("Query {} is valid", formattedQuery);
        } else {
          throw new InvalidRequestException(res.getJsonObject().toString());
        }
      }

    } catch (Exception e) {
      throw new InvalidRequestException(e.getMessage());
    }
  }

  @Override
  public void settingsUpdated() {
    indexManager.checkForUpdate();
    setupIndex(indexManager.getCurrentName());
  }

  @Override
  public boolean isNewIndex() {
    return newIndex.getAndSet(false);
  }

  @Nonnull
  private <T extends JestResult> ResponseHandler<T> execute(Action<T> request) {
    ResponseHandler<T> handler = new ResponseHandler<>(typeName, request);
    client.executeAsync(request, handler);
    return handler;
  }

  @Override
  public Object drop() {
    if (indexConfig.isPerIndexType()) {
      String indexName = indexManager.getCurrentName();
      LOGGER.warn("Index {}/{} is being dropped, stopping purge task, deleting ES index", indexName, typeName);
      indexManager.stop();

      JestResult res = execute(new Delete.Builder("").index(indexName).build()).waitForResult();
      return res.isSucceeded();
    } else {
      return truncate();
    }
  }

  @Override
  public Object truncate() {
    String aliasName = indexManager.getAliasName();
    LOGGER.warn("Index {}/{} is being truncated, deleting documents", aliasName, typeName);
    JestResult res = execute(new Delete.Builder(MATCH_ALL).index(aliasName).type(typeName).build()).waitForResult();
    return res.isSucceeded();
  }

  @Override
  public void deleteExpired() {
    String aliasName = indexManager.getAliasName();
    long ttl = FBUtilities.nowInSeconds() + ttlShift;

    DeleteByQuery deleteQuery = new DeleteByQuery.Builder(String.format(MATCH_LTE, ttl)).addIndex(aliasName).addType(typeName).build();
    JestResult res = execute(deleteQuery).waitForSuccess();

    Long deleted = JsonUtils.getLong(res.getJsonObject(), "deleted");
    if (deleted != null && deleted > 0) {
      LOGGER.debug("Index {} deleted {} documents where _cassandraTtl < {}", indexManager.getAliasName(), deleted, ttl);
    }
  }

  @Override
  public void purgeEmptyIndexes() {
    String aliasName = indexManager.getAliasName();
    LOGGER.debug("Start segmented index cleanup for {}", aliasName);
    JestResult aliasesResponses = execute(new GetAliases.Builder().addIndex(aliasName).build()).waitForResult();

    if (aliasesResponses.isSucceeded()) {
      JsonUtils.getJsonObject(aliasesResponses, aliasName, "aliases").entrySet().forEach(alias -> {
        String indexToDelete = alias.getKey();
        CountResult count = execute(new Count.Builder().addIndex(indexToDelete).build()).waitForResult();

        if (count.isSucceeded() && count.getCount().intValue() == 0) {
          dropIndex(indexToDelete);
        } else {
          LOGGER.debug("Index {} is not empty", indexToDelete);
        }
      });
    }
  }

  @Override
  public void updateIndexConfigOptions() {
    ttlShift = indexConfig.getTtlShift();
    isConcurrentLock = indexConfig.isConcurrentLock();
    jsonFlatSerializedFields = indexConfig.getJsonFlatSerializedFields();
    jsonSerializedFields = indexConfig.getJsonSerializedFields();
    maxResults = indexConfig.getMaxResults();
    isValidateQuery = indexConfig.isValidateQuery();
    isAsyncWrite = indexConfig.isAsyncWrite();
    insertOnly = indexConfig.isInsertOnly();
    usePipeline = StringUtils.isNotBlank(indexConfig.getPipeline(typeName));
    httpPort = indexConfig.getHttpPort();
    indexManager.updateOptions();
  }

  @Override
  public List<String> getIndexNames() {
    List<String> result = new LinkedList<>();
    JestResult res = execute(new GetAliases.Builder().addIndex(indexManager.getAliasName()).build()).waitForResult();
    Set<Map.Entry<String, JsonElement>> set = res.getJsonObject().entrySet();
    for (Map.Entry<String, JsonElement> entry : set) {
      result.add(entry.getKey());
    }
    return result;
  }

  @Override
  public void dropIndex(String indexName) {
    LOGGER.info("Deleting index {}", indexName);
    boolean success = execute(new DeleteIndex.Builder(indexName).build()).waitForResult().isSucceeded();
    LOGGER.info("Index {} deletion {}", indexName, success ? "successful" : "failed");
  }

  private void putAlias(String indexName, String alias) {
    LOGGER.warn("Creating index alias '{}'", indexManager.getAliasName());
    AliasMapping aliases = new AddAliasMapping.Builder(indexName, alias).build();
    JestResult addAliasResult = execute(new ModifyAliases.Builder(aliases).build()).waitForResult();
    LOGGER.warn("Index alias creation result is: {}", addAliasResult.isSucceeded());
  }
}
