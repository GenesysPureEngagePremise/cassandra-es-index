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
 * Jest does not support pipeline yet, this is a custom request using Jest API.
 * <p>
 * https://www.elastic.co/guide/en/elasticsearch/reference/current/put-pipeline-api.html
 * <p>
 * Created by Jacques-Henri Berthemet on 11/07/2017.
 */
public class UpdatePipeline extends GenericResultAbstractAction {

  private final String pipelineId;

  @SuppressWarnings("WeakerAccess") //Don't believe IntelliJ :p
  public UpdatePipeline(@Nonnull UpdatePipeline.Builder builder) {
    super(builder);

    this.pipelineId = builder.pipelineId;
    this.payload = builder.source;
    setURI(buildURI());
  }

  @Override
  protected String buildURI() {
    return "/_ingest/pipeline/" + pipelineId;
  }

  @Override
  public String getRestMethodName() {
    return "PUT";
  }

  public static class Builder extends GenericResultAbstractAction.Builder<UpdatePipeline, UpdatePipeline.Builder> {
    private final String pipelineId;
    private final Object source;

    public Builder(@Nonnull String pipelineId, @Nullable Object source) {
      this.pipelineId = pipelineId;
      this.source = source;
    }

    @Override
    public UpdatePipeline build() {
      return new UpdatePipeline(this);
    }
  }
}
