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
package com.genesyslab.webme.commons.index.config;

import com.google.gson.JsonObject;

import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.exceptions.ConfigurationException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.System.getProperty;
import static java.util.Arrays.asList;

/**
 * The consts defined here are related to Genesys configuration
 * <p>
 * Created by Jacques-Henri Berthemet on 10/24/2014.
 */
public interface IndexConfig {
  // prefix for system options
  String ES_CONFIG_PREFIX = "genesys-es-";
  String ES_FILE = "esi-file"; // For EsSecondaryIndex config
  String ES_ID_SEPARATOR = getProperty(ES_CONFIG_PREFIX + "id-separator", "-");

  // Settings that can be updated on an existing index
  List<String> UPDATABLE_SETTINGS = asList(getProperty(ES_CONFIG_PREFIX + "index.updatable.settings", "refresh_interval").split(","));

  // Both of those consts are for settings that ES 5.x does not support anymore, we need to filter them out
  String[] SETTINGS_TO_SKIP = getProperty(ES_CONFIG_PREFIX + "index.settings.skipped", "").split(",");
  String[] KNOWN_LEGACY_SETTINGS = {"client-only", "cluster-name", "unicast-hosts", "script.disable_dynamic", "cors-enabled",
    "http-enabled", "node-data", "insert-version-type", "update-version-type",
    // ES custom plugins
    "response-filter-regexp-type", "read-only-urls-regexp", "http-read-only", "http-filter", "response-filter-regexp", "log-node-name",
    // UCS E2E
    "network.host", "http.host", "http.port", "transport.tcp.port", "path.data", "path-data", "index.network.host", "index.http.host",
    "index.http.port", "index.transport.tcp.port", "index.path.data", "index.path-data"};

  // Below are configurable options @Since 8.5
  String ES_INDEX_PROPERTIES = "index-properties"; // custom index properties from C* index creation
  // request

  String ES_DUMMY_MODE = "dummy"; // for analytics
  boolean ES_DUMMY_MODE_DEF = false;

  String ES_MAX_RESULTS = "max-results";
  int ES_MAX_RESULTS_DEF = 10000;

  String ES_READ_CL = "read-consistency-level"; // for C* reads
  String ES_READ_CL_DEF = "ONE";

  String ES_ASYNC_WRITE = "async-write";
  boolean ES_ASYNC_WRITE_DEF = true;

  String ES_DISCARD_NULL_VALUES = "discard-nulls";
  boolean ES_DISCARD_NULL_VALUES_DEF = true;

  String ES_INSERT_ONLY = "insert-only";
  boolean ES_INSERT_ONLY_DEF = false;

  String ES_VALIDATE_QUERIES = "validate-queries";
  boolean ES_VALIDATE_QUERIES_DEF = false;

  String ES_SEGMENT = "segment";
  String ES_SEGMENT_DEF = "OFF";

  String ES_SEGMENT_NAME = "segment-name"; // UCS-3731
  String ES_SEGMENT_NAME_DEF = "";

  String ES_PREVENT_CONCURRENT_UPDATES = "concurrent-lock";
  boolean ES_PREVENT_CONCURRENT_UPDATES_DEF = true;

  String ES_SKIP_LOG_REPLAY = "skip-log-replay";
  boolean ES_SKIP_LOG_REPLAY_DEF = true;

  String ES_SKIP_NON_LOCAL_UPDATES = "skip-non-local-updates";
  boolean ES_SKIP_NON_LOCAL_UPDATES_DEF = true;

  String ES_MAPPING_PREFIX = "mapping-";

  String ES_UNICAST_HOSTS = "unicast-hosts";
  String ES_SPECIAL_FIELDS_DELIMITER = ",";

  String ES_JSON_SERIALIZED_FIELDS = "json-serialized-fields"; // String fields that are indexed as
  // JSON
  String ES_JSON_FLAT_SERIALIZED_FIELDS = "json-flat-serialized-fields";

  // New 9.0 options
  String ES_ANALYTIC_MODE = "es-analytic-mode";
  boolean ES_ANALYTIC_MODE_DEF = false;

  String ES_TYPE_PIPELINES = "type-pipelines";
  String ES_PIPELINES_PREFIX = "pipeline-";

  String ES_TRANSLOG = "index.translog.durability";
  String ES_TRANSLOG_ASYNC = "async";

  String ES_INDEX_AVAILABLE_REBUILDING = "available-while-rebuilding";
  boolean ES_INDEX_AVAILABLE_REBUILDING_DEF = true;

  String ES_INDEX_TRUNCATE_REBUILD = "truncate-rebuild";
  boolean ES_INDEX_TRUNCATE_REBUILD_DEF = false;

  String ES_INDEX_PURGE_PERIOD = "purge-period";
  int ES_INDEX_PURGE_PERIOD_DEF = 60; // minutes

  String ES_PER_INDEX_TYPE = "per-index-type";
  boolean ES_PER_INDEX_TYPE_DEF = true; // Required for ES 6.0 as per type mapping will be removed

  String ES_TTL_SHIFT = "ttl-shift";
  int ES_TTL_SHIFT_DEF = 0; // seconds

  String ES_FORCE_DELETE = "force-delete";
  boolean ES_FORCE_DELETE_DEF = false;

  String ES_INDEX_MANAGEMENT = "index-manager";
  String ES_INDEX_MANAGEMENT_DEF = "com.genesyslab.webme.commons.index.DefaultIndexManager";

  String ES_HTTP_PORT = "http-port";
  int ES_HTTP_PORT_DEF = 9200;

  String ES_MAX_CONNECTION_PER_ROUTE = "max-connections-per-route";
  int ES_MAX_CONNECTION_PER_ROUTE_DEF = 2; //http://dev.bizo.com/2013/04/sensible-defaults-for-apache-httpclient.html

  String ES_RETRY_ON_CONFLICT = "retry-on-conflict";
  int ES_RETRY_ON_CONFLICT_DEF = 5;


  @Nonnull
  Set<String> getPipelines();

  @Nullable
  String getPipeline(@Nonnull String type) throws ConfigurationException;

  /**
   * @return additional properties for ES index client node, not null!
   */
  @Nonnull
  JsonObject getProperties();

  /**
   * @return Maximum number of results to ask to ES
   */
  int getMaxResults();

  /**
   * Get the mapping for defined type
   *
   * @param name the name of the type to get getMapping for
   * @return the getMapping as a json string
   */
  @Nullable
  String getTypeMapping(@Nonnull String name) throws ConfigurationException;

  /**
   * @return the list of unicast hosts
   */
  @Nullable
  String getUnicastHosts();

  /**
   * @return false if null values should be discarded, default is true.
   */
  boolean isDiscardNullValues();

  /**
   * https://intranet.genesys.com/display/GPE/WCC+ElasticSearch+Cassandra+Index#WCCElasticSearchCassandraIndex-json-serialized-fields
   *
   * @return returns all the string fields that are json serialized
   */
  @Nonnull
  Set<String> getJsonSerializedFields();

  /**
   * @return use async ES operations
   */
  boolean isAsyncWrite();

  /**
   * @return the ConsistencyLevel used to read Cassandra rows
   */
  @Nonnull
  ConsistencyLevel getReadConsistencyLevel();

  /**
   * @return index segment timeframe
   */
  @Nullable
  Segment getIndexSegment();

  /**
   * To set a specific segment name (format is alias-index@segmentname), set segment type to CUSTOM
   *
   * @return index custom segment name
   */
  @Nullable
  String getIndexSegmentName(); // UCS-3731

  /**
   * @return the options provided in Cassandra create index request
   */
  @Nonnull
  Map<String, String> getIndexOptions();

  /**
   * https://intranet.genesys.com/display/GPE/WCC+ElasticSearch+Cassandra+Index#WCCElasticSearchCassandraIndex-json-flat-serialized-fields
   *
   * @return returns all the string fields that are json serialized but values are treated as
   * strings, used when values are not always of the same type
   */
  @Nonnull
  Set<String> getJsonFlatSerializedFields();

  /**
   * @return true if request should be validated before execution
   */
  boolean isValidateQuery();

  /**
   * @return if we need to skip startup commit log replay
   */
  boolean isSkipLogReplay();

  /**
   * @return if we discard updates sent to us as a replica
   */
  boolean isSkipNonLocalUpdates();

  /**
   * @return prevent concurrent updates on the same PK for the same type
   */
  boolean isConcurrentLock();

  /**
   * @return true for analytics mode where we disable delete operation
   */
  boolean isAnalyticMode();

  /**
   * Reload this config with new options from Cassandra, file options if present still override
   * those options
   *
   * @param cassandraOptions from index
   * @return true if changed
   */
  boolean reload(@Nonnull Map<String, String> cassandraOptions);

  boolean isIndexAvailableWhenBuilding();

  boolean isTruncateBeforeRebuild();

  /**
   * @return in minutes
   */
  int getIndexPurgePeriod();

  /**
   * @return each type will have its own index using keyspace_ as prefix
   */
  boolean isPerIndexType();

  /**
   * @return when delete by query to clean ttled docs it's possible to shift that time to keep docs
   * longer in ES, default is 0
   */
  int getTtlShift();

  /**
   * @return periodically delete all document where cassandra TTL expired without relying on
   * compaction, required with TTL shift
   */
  boolean isForceDelete();

  boolean isDummyMode();

  boolean isInsertOnly();

  String getIndexManagerName();

  int getHttpPort();

  int getMaxTotalConnectionPerRoute();

  /**
   * For updates only
   */
  int getRetryOnConflict();

  /**
   * HOUR is for testing purposes only!!! Not for production usage
   */
  enum Segment {
    OFF,
    HOUR,
    DAY,
    TEN,
    MONTH,
    YEAR,
    CUSTOM // WCC-862 UCS-3731 Free format for the index name (ie not time related). Format label can be anything... accepted by ES ;)
  }
}
