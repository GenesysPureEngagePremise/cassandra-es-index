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
package com.genesyslab.webme.commons.index.monitor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * This interface allows to expose ES client as JMX interface
 * Created by Jacques-Henri Berthemet on 29/07/2016.
 */
public interface EsJmxBridgeMXBean {

  String NAME = "com.genesyslab.webme.commons.index.monitor:type=EsJmxBridge";

  /**
   * @return server side timeout for executing requests
   */
  long getTimeout();

  /**
   * @param timeout server side timeout for executing requests
   */
  void setTimeout(long timeout);

  /**
   * @return the cluster status as a Json string
   */
  @Nonnull
  String getClusterState();

  /**
   * @return cluster statistics as a Json string
   */
  @Nonnull
  String getClusterStats();

  /**
   * @return pending tasks as a Json string
   */
  @Nonnull
  String getPendingClusterTasks();

  /**
   * @since 8.5.000.66
   */
  @Nonnull
  String getClusterHealth();

  /**
   * @since 8.5.000.66
   */
  @Nonnull
  String getIndicesStats();

  /**
   * @since 8.5.000.66
   */
  @Nonnull
  String getNodesStats();

  /**
   * Execute a generic command on ES cluster<br>
   * WARNING this changed between 8.5 and 9.0
   *
   * @param methodPath METHOD{space}URI for example "GET ucs/_aliases"
   * @param request    json UTF8 string if needed as a byte array
   * @return UTF8 JSON string or null in some cases
   * @throws IOException is request is not a success, with "404 Not Found" or just "404" is not message was returned
   */
  @Nullable
  byte[] execute(@Nonnull String methodPath, @Nullable byte[] request)
    throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException, IOException;

  /**
   * @return true
   */
  boolean connected();

}
