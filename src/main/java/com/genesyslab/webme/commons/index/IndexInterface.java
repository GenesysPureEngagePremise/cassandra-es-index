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

import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.utils.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Created by Jacques-Henri Berthemet on 10/24/2014.
 */
public interface IndexInterface {

  /**
   * Perform any initialization work
   */
  void init();

  /**
   * Reads the getMapping
   */
  SearchResult getMapping(String index);

  /**
   * Updates the getMapping
   */
  SearchResult putMapping(String index, String source);

  /**
   * Index a new document
   *
   * @param partitionKeys  not null, not empty
   * @param elements       not null, not empty
   * @param expirationTime in seconds
   * @param isInsert       if false will use update
   * @throws IOException if something goes wrong
   */
  void index(@Nonnull List<Pair<String, String>> partitionKeys, @Nonnull List<CellElement> elements, long expirationTime, boolean isInsert)
    throws IOException;

  /**
   * Delete the corresponding document
   *
   * @param partitionKeys not null, not empty
   */
  void delete(@Nonnull List<Pair<String, String>> partitionKeys);

  /**
   * Flush all data from memory to disk
   */
  @Nullable
  Object flush();

  /**
   * Truncate the index
   */
  @Nullable
  Object truncate();

  /**
   * Drop the index
   */
  @Nullable
  Object drop();

  /**
   * Find results matching the query string with matching partitionKeys, clusteringColumnsNames
   *
   * @param queryMetaData not null, the query to execute
   * @return not null, SearchResult which contains a list SearchResultRows and result metadata as
   * Json string
   */
  @Nonnull
  SearchResult search(@Nonnull QueryMetaData queryMetaData);

  /**
   * @param query the query to validate
   */
  void validate(@Nonnull String query) throws InvalidRequestException;

  /**
   * Something may have changed in the configuration
   */
  void settingsUpdated();

  /**
   * @return true _once_ if index was newly created
   */
  boolean isNewIndex();

  /**
   * Delete indexes that have no documents
   */
  void purgeEmptyIndexes();

  /**
   * Enforce Cassandra TTL expiration
   */
  void deleteExpired();

  /**
   * Reload options from index config
   */
  void updateIndexConfigOptions();

  /**
   * Delete index with specified name
   *
   * @param indexName index name
   */
  void dropIndex(String indexName);

  /**
   * Returns list of index names for current alias
   */
  List<String> getIndexNames();

}
