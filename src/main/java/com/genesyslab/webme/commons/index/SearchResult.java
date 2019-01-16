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

import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

/**
 * Created by Julien Moreau on 1/6/2017.
 */
public class SearchResult {

  public final List<SearchResultRow> items;
  /**
   * Global ES metadata, like time spent, shards etc ...
   */
  public final JsonObject metadata;

  public SearchResult(@Nonnull List<SearchResultRow> items, @Nullable JsonObject metadata) {
    this.items = items;
    this.metadata = metadata;
  }
}
