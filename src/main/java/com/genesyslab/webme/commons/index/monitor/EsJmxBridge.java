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

import com.genesyslab.webme.commons.index.requests.GenericRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.cluster.Health;
import io.searchbox.cluster.NodesStats;
import io.searchbox.cluster.PendingClusterTasks;
import io.searchbox.cluster.State;
import io.searchbox.cluster.Stats;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by Jacques-Henri Berthemet on 29/07/2016.
 */
public class EsJmxBridge implements EsJmxBridgeMXBean {

  private static final Logger LOGGER = LoggerFactory.getLogger(EsJmxBridge.class);

  private final JestClient client;

  public EsJmxBridge(JestClient client)
    throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
    this.client = client;

    LOGGER.info("Registering ES JMX bridge");
    //Get the MBean server and register the MBean
    ManagementFactory.getPlatformMBeanServer().registerMBean(this, new ObjectName(NAME));
    LOGGER.info("Registration of '{}' successful", NAME);
  }

  @Override
  public long getTimeout() {
    return -1;
  }

  @Override
  public void setTimeout(long timeout) {
  }

  @Override
  @Nonnull
  public String getClusterState() {
    return execute(new State.Builder().build()).getJsonString();
  }

  @Override
  @Nonnull
  public String getClusterStats() {
    return execute(new Stats.Builder().build()).getJsonString();
  }

  @Override
  @Nonnull
  public String getPendingClusterTasks() {
    return execute(new PendingClusterTasks.Builder().build()).getJsonString();
  }

  @Override
  @Nonnull
  public String getClusterHealth() {
    return execute(new Health.Builder().build()).getJsonString();
  }

  @Override
  @Nonnull
  public String getIndicesStats() {
    return execute(new io.searchbox.indices.Stats.Builder().build()).getJsonString();
  }

  @Override
  @Nonnull
  public String getNodesStats() {
    return execute(new NodesStats.Builder().build()).getJsonString();
  }

  @Override
  public boolean connected() {
    return true;
  }

  @Override
  @Nullable
  public byte[] execute(@Nonnull String methodPath, @Nullable byte[] request) throws IOException {
    int pos = methodPath.indexOf(' ');
    String method = methodPath.substring(0, pos);
    String url = methodPath.substring(pos + 1);
    String payload = (request == null || request.length == 0) ? null : new String(request, UTF_8);

    JestResult result = execute(new GenericRequest(method, url, payload));

    if (!result.isSucceeded()) {
      throw result.getErrorMessage() == null
        ? new IOException(String.valueOf(result.getResponseCode()))
        : new IOException(result.getResponseCode() + " " + result.getErrorMessage());
    }

    return result.getJsonString() == null ? new byte[0] : result.getJsonString().getBytes(UTF_8);
  }

  private <T extends JestResult> T execute(Action<T> request) {
    try {
      return client.execute(request);
    } catch (IOException e) {
      LOGGER.error("Failed to send ES request:{}", request, e);
      throw new RuntimeException(e);
    }
  }
}
