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

import com.google.gson.Gson;

import org.junit.Test;

/**
 * @author Jacques-Henri Berthemet 03/08/2018
 */
public class BuildTest {

  @Test
  public void ensureJestVersion() {
    new io.searchbox.core.SearchResult(new Gson()).getTotal(); //UCS-5297 will throw method not found on bad versions
  }
}
