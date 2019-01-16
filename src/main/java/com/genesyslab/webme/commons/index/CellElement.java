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

import org.apache.cassandra.utils.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

/**
 * Holds all cell values for ES operations<br>
 * Warning: hashCode() and equals() only uses "name" value.
 * <p>
 * Created by Jacques-Henri Berthemet on 11/10/2014.
 */
public class CellElement {

  List<Pair<String, String>> clusteringKeys;
  public String name;
  public String value;
  CollectionValue collectionValue; //can be null if CF has no collections

  public static CellElement create(@Nonnull String name, @Nullable String value, @Nullable CollectionValue collectionValue) {
    CellElement elem = new CellElement();
    elem.name = name;
    elem.value = value;
    elem.collectionValue = collectionValue;
    return elem;
  }

  boolean isCollection() {
    return collectionValue != null;
  }

  @Override
  public int hashCode() {
    return name == null ? 0 : name.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof CellElement && other.hashCode() == hashCode();
  }

  public static class CollectionValue {
    String name;
    String value;
    CollectionType type;

    @Nonnull
    public static CollectionValue create(@Nonnull String name, @Nullable String value, @Nonnull CollectionType type) {
      CollectionValue val = new CollectionValue();
      val.name = name;
      val.value = value;
      val.type = type;
      return val;
    }

    public enum CollectionType {
      MAP,
      /**
       * JSON is used for UDT and tuples
       **/
      JSON,
      SET,
      LIST
    }
  }

}
