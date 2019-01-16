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

import io.searchbox.action.GenericResultAbstractAction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Created by Jacques-Henri Berthemet on 11/07/2017.
 */
public class GenericRequest extends GenericResultAbstractAction {

  private final String method;
  private final String url;

  public GenericRequest(@Nonnull String method, @Nonnull String url, @Nullable Object payload) {
    super();
    this.payload = payload;
    this.method = method;
    this.url = url;
    setURI(buildURI());
  }

  @Override
  public String getRestMethodName() {
    return method;
  }

  @Override
  protected String buildURI() {
    return url;
  }
}
