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

import org.apache.cassandra.exceptions.ExceptionCode;
import org.apache.cassandra.exceptions.RequestExecutionException;

/**
 * @author Jacques-Henri Berthemet 14/09/2017
 */
public class EsRequestExecutionException extends RequestExecutionException {

  public EsRequestExecutionException(int code, String message) {
    super(ExceptionCode.SERVER_ERROR, code + ":" + message);
  }
}
