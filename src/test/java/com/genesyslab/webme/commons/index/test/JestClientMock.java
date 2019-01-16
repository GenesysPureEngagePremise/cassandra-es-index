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

import com.google.gson.Gson;

import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.client.JestResultHandler;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Created by Jacques-Henri Berthemet on 12/07/2017.
 */
public class JestClientMock implements JestClient {

  public static final List<Action<?>> receivedRequests = new LinkedList<>();
  private static final Queue<JestResult> expectedResults = new LinkedList<>();
  private static final Map<String, JestResult> expectedMapResults = new HashMap<>();
  private static final JestResult EMPTY_RESULT;
  public static Action lastRequest;

  static {
    EMPTY_RESULT = new JestResult(new Gson());
    EMPTY_RESULT.setSucceeded(true);
  }

  /**
   * @param result the next result that will be returned to from the Jest client
   */
  public static void addResponse(JestResult result) {
    expectedResults.add(result);
  }

  public static boolean hasMoreResponses() {
    return !expectedResults.isEmpty();
  }

  private static String toString(Action clientRequest) {
    return clientRequest.toString() + clientRequest.getData(new Gson());
  }

  /**
   * remove all expected responses
   */
  public static void clear() {
    expectedResults.clear();
    expectedMapResults.clear();
    receivedRequests.clear();
    lastRequest = null;
  }

  @Override
  public <T extends JestResult> T execute(Action<T> clientRequest) {
    return fakeExecute(clientRequest);
  }

  @SuppressWarnings("unchecked")
  private <T extends JestResult> T fakeExecute(Action<T> clientRequest) {
    lastRequest = clientRequest;
    receivedRequests.add(clientRequest);
    String key = toString(clientRequest);
    JestResult res = (expectedMapResults.containsKey(key) ? expectedMapResults.get(key) : expectedResults.poll());
    return (T) (res == null ? EMPTY_RESULT : res);
  }

  @Override
  public <T extends JestResult> void executeAsync(Action<T> clientRequest, JestResultHandler<? super T> jestResultHandler) {
    jestResultHandler.completed(fakeExecute(clientRequest));
  }

  @Override
  @Deprecated
  public void shutdownClient() {
  }

  @Override
  public void setServers(Set<String> servers) {
  }

  //@Override
  public void close() {
  }
}
