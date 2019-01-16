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

import com.genesyslab.webme.commons.index.config.IndexConfig;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.ZoneOffset.UTC;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Manages index creation on top of current date by configured template. Provides data expiration on
 * top of additional field in every document
 *
 * @author Jacques-Henri Berthemet 04/09/2017
 */
public class DefaultIndexManager implements IndexManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIndexManager.class);

  public static final String INDEX_POSTFIX = "_index@";
  private static final long UPDATE_PERIOD = Long.getLong(IndexConfig.ES_CONFIG_PREFIX + "update-period", 60);
  private static final long TTL_PERIOD = Long.getLong(IndexConfig.ES_CONFIG_PREFIX + "ttl-period", 60);

  // Make sure there is one thread per task
  private final AtomicInteger threadId = new AtomicInteger(0);
  private final ScheduledExecutorService scheduler =
    Executors.newScheduledThreadPool(3, r -> new Thread(r, "IdxMgr-" + threadId.getAndIncrement()));
  private final ElasticIndex index;
  private final IndexConfig indexConfig;
  private final String aliasName; // Never changes
  private String currentName;

  public DefaultIndexManager(@Nonnull ElasticIndex index, @Nonnull IndexConfig indexConfig, @Nonnull String indexName) {
    this.index = index;
    this.indexConfig = indexConfig;

    // index and alias names must be lowercase
    aliasName = (indexConfig.isPerIndexType() ? indexName + "_" + index.typeName : indexName).toLowerCase();

    long purgePeriod = indexConfig.getIndexPurgePeriod();
    scheduler.scheduleAtFixedRate(new SafeRunnable(index::purgeEmptyIndexes, "purge", LOGGER), purgePeriod, purgePeriod, MINUTES);
    if (indexConfig.isForceDelete()) {
      scheduler.scheduleAtFixedRate(new SafeRunnable(index::deleteExpired, "ttl", LOGGER), TTL_PERIOD, TTL_PERIOD, SECONDS);
    }

    scheduler.scheduleAtFixedRate(new SafeRunnable(this::checkForUpdate, "update", LOGGER), UPDATE_PERIOD, UPDATE_PERIOD, SECONDS);
    currentName = buildIndexName();
  }

  /**
   * @return the constant name of the index
   */
  @Override
  @Nonnull
  public String getAliasName() {
    return aliasName;
  }

  /**
   * @return the current name of the latest index
   */
  @Override
  @Nonnull
  public String getCurrentName() {
    return currentName;
  }

  @Override
  public void stop() {
    if (!scheduler.isShutdown()) {
      LOGGER.debug("Canceling IndexManager thread for {}", aliasName);
      scheduler.shutdownNow();
    }
  }

  @Nonnull
  private String buildIndexName() {
    StringBuilder indexNameBuilder = new StringBuilder();

    indexNameBuilder.append(aliasName).append(INDEX_POSTFIX);

    if (indexConfig.getIndexSegment() != null) {
      switch (indexConfig.getIndexSegment()) {
        case CUSTOM:
          if (indexConfig.getIndexSegmentName() == null) {
            throw new ConfigurationException(indexConfig.getIndexSegment() + " mode can't have a null name");
          } else {
            indexNameBuilder.append(indexConfig.getIndexSegmentName().toLowerCase()); // WCC-862 // UCS-3731
          }
          break;
        case OFF:
          break;
        default:
          String format = getDateFormat(indexConfig.getIndexSegment());
          if (format != null) {
            SimpleDateFormat sf = new SimpleDateFormat(format);
            sf.setTimeZone(TimeZone.getTimeZone(UTC));
            indexNameBuilder.append(sf.format(new Date()));
          }
          break;
      }
    }
    return indexNameBuilder.toString();
  }

  @Nullable
  private String getDateFormat(@Nonnull IndexConfig.Segment segment) {
    switch (segment) {
      case YEAR:
        return "yyyy";
      case MONTH:
        return "yyyy-MM";
      case OFF:
        return null;
      case HOUR:
        return "yyyy-MM-dd-HH";
      case CUSTOM: // WCC-862 UCS-3731
        return null;
      case DAY:
      default:
        return "yyyy-MM-dd";
    }
  }

  @Override
  public void checkForUpdate() {
    String newName = buildIndexName();

    if (!newName.equals(currentName)) {
      LOGGER.debug("Index {} current name changed from {} to {}, updating configuration", aliasName, currentName, newName);
      index.setupIndex(newName);
      currentName = newName;
      LOGGER.warn("Index {}/{} configuration updated", aliasName, currentName);
    }
  }

  @Override
  public void updateOptions() {
  }

  @Override
  public boolean isTTLFieldRequired() {
    return true;
  }
}
