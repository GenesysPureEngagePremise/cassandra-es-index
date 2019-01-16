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

import java.util.List;

import static java.util.Collections.emptyList;

/**
 * @author Jacques-Henri Berthemet 14/09/2017
 */
public class EsDummyIndex implements IndexInterface {

  @Override
  public void init() {
  }

  @Override
  public SearchResult getMapping(String indexName) {
    return new SearchResult(emptyList(), null);
  }

  @Override
  public SearchResult putMapping(String indexName, String source) {
    return new SearchResult(emptyList(), null);
  }

  @Override
  public void index(@Nonnull List<Pair<String, String>> partitionKeys, @Nonnull List<CellElement> elements, long expirationTime,
    boolean isInsert) {
  }

  @Override
  public void delete(@Nonnull List<Pair<String, String>> partitionKeys) {
  }

  @Nullable
  @Override
  public Object flush() {
    return null;
  }

  @Nullable
  @Override
  public Object truncate() {
    return null;
  }

  @Nullable
  @Override
  public Object drop() {
    return null;
  }

  @Nonnull
  @Override
  public SearchResult search(@Nonnull QueryMetaData queryMetaData) {
    return new SearchResult(emptyList(), null);
  }

  @Override
  public void validate(@Nonnull String query) throws InvalidRequestException {
  }

  @Override
  public void settingsUpdated() {
  }

  @Override
  public boolean isNewIndex() {
    return false;
  }

  @Override
  public void purgeEmptyIndexes() {
  }

  @Override
  public void deleteExpired() {
  }

  @Override
  public void updateIndexConfigOptions() {
  }

  @Override
  public List<String> getIndexNames() {
    return emptyList();
  }

  @Override
  public void dropIndex(String indexName) {
  }
}
