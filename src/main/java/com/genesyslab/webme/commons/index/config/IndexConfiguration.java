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

import com.genesyslab.webme.commons.index.requests.ElasticClientFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.exceptions.ConfigurationException;

import javax.annotation.Nonnull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author Jacques-Henri Berthemet
 */
public class IndexConfiguration implements IndexConfig {
  private static final boolean ES_CLASSES_NOT_FOUND;


  static {
    boolean notFound;
    try {
      Class.forName(ElasticClientFactory.REST_CLIENT_CLASS);
      notFound = false;
    } catch (ClassNotFoundException e) {
      notFound = true;
    }

    ES_CLASSES_NOT_FOUND = notFound;
  }

  private final OptionReader reader;

  public IndexConfiguration(@Nonnull String name, @Nonnull Map<String, String> cassandraOptions) {
    this.reader = new OptionReaderImpl(name, cassandraOptions);
  }

  @Override
  @Nonnull
  public Set<String> getPipelines() {
    String value = reader.getString(ES_TYPE_PIPELINES, null);
    Set<String> types = new HashSet<>();

    if (value != null) {
      for (String type : value.split(",")) {
        if (!Strings.isNullOrEmpty(type)) {
          types.add(type);
        }
      }
    }

    types.addAll(getPipelinesFromOptions()); // also we add all pipelines defined as "ES_PIPELINES_PREFIX + typeName"
    return types;
  }

  @Nonnull
  private Set<String> getPipelinesFromOptions() {
    Set<String> result = new HashSet<>();

    reader.getOptions().forEach((key, value) -> {
      if (key.startsWith(ES_PIPELINES_PREFIX)) {
        result.add(key.substring(ES_PIPELINES_PREFIX.length()));
      }
    });
    return result;
  }

  @Override
  public String getPipeline(@Nonnull String typeName) throws ConfigurationException {
    return reader.getString(ES_PIPELINES_PREFIX + typeName, null);
  }

  @Override
  @Nonnull
  public JsonObject getProperties() {
    String indexProperties = reader.getOptions().get(ES_INDEX_PROPERTIES);

    if (indexProperties != null) {
      return (JsonObject) new JsonParser().parse(indexProperties);
    } else {
      return new JsonObject();
    }
  }

  @Override
  public int getMaxResults() {
    return reader.getInteger(ES_MAX_RESULTS, ES_MAX_RESULTS_DEF);
  }

  @Override
  public String getTypeMapping(@Nonnull String typeName) throws ConfigurationException {
    return reader.getString(ES_MAPPING_PREFIX + typeName, null);
  }

  @Override
  public String getUnicastHosts() {
    return reader.getString(ES_UNICAST_HOSTS, null);
  }

  @Override
  public boolean isDiscardNullValues() {
    return reader.getBoolean(ES_DISCARD_NULL_VALUES, ES_DISCARD_NULL_VALUES_DEF);
  }

  @Override
  public boolean isAsyncWrite() {
    return reader.getBoolean(ES_ASYNC_WRITE, ES_ASYNC_WRITE_DEF);
  }

  @Override
  @Nonnull
  public ConsistencyLevel getReadConsistencyLevel() {
    return ConsistencyLevel.valueOf(reader.getString(ES_READ_CL, ES_READ_CL_DEF));
  }

  @Override
  public boolean isAnalyticMode() {
    return reader.getBoolean(ES_ANALYTIC_MODE, ES_ANALYTIC_MODE_DEF);
  }

  @Override
  public boolean reload(@Nonnull Map<String, String> options) {
    return reader.reload(options);
  }

  @Override
  public boolean isIndexAvailableWhenBuilding() {
    return reader.getBoolean(ES_INDEX_AVAILABLE_REBUILDING, ES_INDEX_AVAILABLE_REBUILDING_DEF);
  }

  @Override
  public boolean isTruncateBeforeRebuild() {
    return reader.getBoolean(ES_INDEX_TRUNCATE_REBUILD, ES_INDEX_TRUNCATE_REBUILD_DEF);
  }

  @Override
  public int getIndexPurgePeriod() {
    return reader.getInteger(ES_INDEX_PURGE_PERIOD, ES_INDEX_PURGE_PERIOD_DEF);
  }

  @Override
  public boolean isPerIndexType() {
    return reader.getBoolean(ES_PER_INDEX_TYPE, ES_PER_INDEX_TYPE_DEF);
  }

  @Override
  public int getTtlShift() {
    return reader.getInteger(ES_TTL_SHIFT, ES_TTL_SHIFT_DEF);
  }

  @Override
  public boolean isForceDelete() {
    return reader.getBoolean(ES_FORCE_DELETE, ES_FORCE_DELETE_DEF);
  }

  @Override
  public boolean isDummyMode() {
    return ES_CLASSES_NOT_FOUND || reader.getBoolean(ES_DUMMY_MODE, ES_DUMMY_MODE_DEF);
  }

  @Override
  public boolean isInsertOnly() {
    return reader.getBoolean(ES_INSERT_ONLY, ES_INSERT_ONLY_DEF);
  }

  @Override
  public Segment getIndexSegment() {
    return Segment.valueOf(reader.getString(ES_SEGMENT, ES_SEGMENT_DEF));
  }

  @Override
  public String getIndexSegmentName() {
    return reader.getString(ES_SEGMENT_NAME, ES_SEGMENT_NAME_DEF);
  }

  @Override
  @Nonnull
  public Map<String, String> getIndexOptions() {
    return reader.getOptions();
  }

  @Override
  @Nonnull
  public Set<String> getJsonFlatSerializedFields() {
    return getFields(ES_JSON_FLAT_SERIALIZED_FIELDS);
  }

  @Nonnull
  @Override
  public Set<String> getJsonSerializedFields() {
    return getFields(ES_JSON_SERIALIZED_FIELDS);
  }

  @Nonnull
  private Set<String> getFields(@Nonnull String name) {
    Set<String> fields = new HashSet<>();
    String value = reader.getString(name, null);
    if (value != null) {
      StringTokenizer tokenizer = new StringTokenizer(value, ES_SPECIAL_FIELDS_DELIMITER);
      while (tokenizer.hasMoreElements()) {
        fields.add(tokenizer.nextToken());
      }
    }
    return ImmutableSet.copyOf(fields);
  }

  @Override
  public boolean isValidateQuery() {
    return reader.getBoolean(ES_VALIDATE_QUERIES, ES_VALIDATE_QUERIES_DEF);
  }

  @Override
  public boolean isSkipLogReplay() {
    return reader.getBoolean(ES_SKIP_LOG_REPLAY, ES_SKIP_LOG_REPLAY_DEF);
  }

  @Override
  public boolean isSkipNonLocalUpdates() {
    return reader.getBoolean(ES_SKIP_NON_LOCAL_UPDATES, ES_SKIP_NON_LOCAL_UPDATES_DEF);
  }

  @Override
  public boolean isConcurrentLock() {
    return reader.getBoolean(ES_PREVENT_CONCURRENT_UPDATES, ES_PREVENT_CONCURRENT_UPDATES_DEF);
  }

  @Override
  public String getIndexManagerName() {
    return reader.getString(ES_INDEX_MANAGEMENT, ES_INDEX_MANAGEMENT_DEF);
  }

  @Override
  public int getHttpPort() {
    return reader.getInteger(ES_HTTP_PORT, ES_HTTP_PORT_DEF);
  }

  @Override
  public int getMaxTotalConnectionPerRoute() {
    return reader.getInteger(ES_MAX_CONNECTION_PER_ROUTE, ES_MAX_CONNECTION_PER_ROUTE_DEF);
  }

  @Override
  public int getRetryOnConflict() {
    return reader.getInteger(ES_RETRY_ON_CONFLICT, ES_RETRY_ON_CONFLICT_DEF);
  }

}
