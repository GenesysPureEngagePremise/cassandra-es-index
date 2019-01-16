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
package com.genesyslab.webme.commons.index.requests;

import io.searchbox.client.JestClientFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This is useful for tests
 * <p>
 * Created by Jacques-Henri Berthemet on 12/07/2017.
 *
 */
public class ElasticClientFactory {

  public static final String REST_CLIENT_CLASS = "io.searchbox.client.JestClientFactory";
  private static JestClientFactory factory;

  /**
   * @return a new instance of JestClientFactory or whatever was set using setJestClientFactory()
   */
  @Nonnull
  public static JestClientFactory getJestClientFactory() {
    return factory == null ? new JestClientFactory() : factory;
  }


  /**
   * @param newFactory a factory that should be used by all classes that need a JestClientFactory, usually for testing
   * @return the previous value
   */
  @Nullable
  public static JestClientFactory setJestClientFactory(@Nullable JestClientFactory newFactory) {
    JestClientFactory prev = factory;
    factory = newFactory;
    return prev;
  }
}
