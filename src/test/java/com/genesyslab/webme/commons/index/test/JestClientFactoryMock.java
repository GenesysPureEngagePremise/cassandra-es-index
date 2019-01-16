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
package com.genesyslab.webme.commons.index.test;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;

/**
 * Created by Jacques-Henri Berthemet on 12/07/2017.
 */
public class JestClientFactoryMock extends JestClientFactory {

  public static final JestClientFactoryMock INSTANCE = new JestClientFactoryMock();
  public static HttpClientConfig httpConfig;
  private final JestClient client = new JestClientMock();

  public JestClient getObject() {
    return client;
  }

  public void setHttpClientConfig(HttpClientConfig httpClientConfig) {
    super.setHttpClientConfig(httpClientConfig);
    httpConfig = httpClientConfig;
  }
}
