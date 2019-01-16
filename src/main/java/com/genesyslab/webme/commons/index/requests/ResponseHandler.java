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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.action.Action;
import io.searchbox.client.JestResult;
import io.searchbox.client.JestResultHandler;

import javax.annotation.Nonnull;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.genesyslab.webme.commons.index.EsSecondaryIndex.DEBUG_SHOW_VALUES;

/**
 * Wraps a Jest response to allow logging and blocking waiting for a response
 *
 * @author Jacques-Henri Berthemet 19/07/2017
 */
public class ResponseHandler<T extends JestResult> implements JestResultHandler<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResponseHandler.class);
  private static final String JSON_FMT = "%d %s %s in %s";
  private static final String SHORT_FMT = "%d %s %s";

  private final String typeName;
  private final Action<T> request;
  private final Semaphore semaphore = new Semaphore(0);
  private final AtomicBoolean isCompleted = new AtomicBoolean();
  private T result;
  private Exception exception;

  public ResponseHandler(@Nonnull String typeName, @Nonnull Action<T> request) {
    this.typeName = typeName;
    this.request = request;
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Handling ES request #{} {} {}", request.hashCode(), request.toString(), typeName);
    }
  }

  @Override
  public void completed(@Nonnull T result) {
    this.result = result;
    isCompleted.set(true);
    semaphore.release();
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Completed ES request #{} {} {}", request.hashCode(), request.toString(), typeName);
    }
  }

  @Override
  public void failed(@Nonnull Exception ex) {
    this.exception = ex;
    isCompleted.set(true);
    semaphore.release();
    LOGGER.error("Failed ES request #{} {} {} {}", request.hashCode(), request.toString(), typeName, ex);
  }

  /**
   * This is a blocking call until a successful response have been received, next calls are not blocking.
   *
   * @return the response
   * @throws EsRequestExecutionException if response is not an HTTP success
   */
  @Nonnull
  public T waitForSuccess() {
    return waitForResult(true);
  }

  /**
   * This is a blocking call until a response have been received with the one of expected codes, next calls are not blocking.
   *
   * @param codes a list of acceptable HTTP response codes
   * @throws EsRequestExecutionException if response is not one of the provided HTTP codes
   */
  public void waitForStatus(int... codes) {
    result = waitForResult();
    for (int code : codes) {
      if (code == result.getResponseCode()) {
        return;
      }
    }

    throw new EsRequestExecutionException(result.getResponseCode(), result.getErrorMessage());
  }

  /**
   * This is a blocking call until a response or an exception have been received, next calls are not blocking.
   *
   * @return the response
   */
  @Nonnull
  public T waitForResult() {
    return waitForResult(false);
  }

  @Nonnull
  private T waitForResult(boolean assertSuccess) {
    if (!isCompleted.get()) {
      try {
        semaphore.acquire();
        semaphore.release(); //potentially release other threads
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    if (exception != null) {
      throw new RuntimeException(exception);
    }

    if (assertSuccess && !result.isSucceeded()) {
      LOGGER.error("Received error to request #{} {} {}, details: {}",
        request.hashCode(), request.toString(), typeName, resultString(result, true));
      throw new EsRequestExecutionException(result.getResponseCode(), result.getErrorMessage());

    } else if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Received response to request #{} {} {}, details: {}",
        request.hashCode(), request.toString(), typeName, resultString(result, DEBUG_SHOW_VALUES));
    }

    return result;
  }

  @Nonnull
  private String resultString(@Nonnull T res, boolean showJson) {
    if (showJson) {
      return String.format(JSON_FMT, res.getResponseCode(), res.getErrorMessage(), res.getPathToResult(), res.getJsonString());
    } else {
      return String.format(SHORT_FMT, res.getResponseCode(), res.getErrorMessage(), res.getPathToResult());
    }
  }
}
