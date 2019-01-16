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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Manages index creation by splitting timeline from EPOH to now into discrete and equals ranges.
 * Provides data expiration on top of calculation whicj index does not contains actual data and
 * drops indexes.
 *
 * @author Sergey Feoktistov
 */
@SuppressWarnings("unused")
public class IndexDropManager implements IndexManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(IndexDropManager.class);

  private static final String INDEX_POSTFIX = "_index@";
  private static final String TIMESTAMP_FORMAT = "yyyyMMdd't'HHmmss'z'";

  private static final String INDEX_SEGMENT_SIZE = "segment-size";
  private static final long DAY = 24L * 60L * 60L * 1000; // 1 day

  // Make sure there is one thread per task
  private final AtomicInteger threadId = new AtomicInteger(0);
  private final ScheduledExecutorService scheduler =
    Executors.newScheduledThreadPool(2, r -> new Thread(r, "IdxDrMgr-" + threadId.getAndIncrement()));
  private ScheduledFuture<?> ttlTask;

  private final ElasticIndex index;
  private final IndexConfig indexConfig;
  private final DateFormat tsFormat;
  private final String aliasName;
  private String currentName;
  private long segmentFrame; // index segment frame in milliseconds
  private long ttlShift;

  private static final long UPDATE_PERIOD = Long.getLong(IndexConfig.ES_CONFIG_PREFIX + "update-period", 60);

  public IndexDropManager(@Nonnull ElasticIndex index, @Nonnull IndexConfig indexConfig, @Nonnull String indexName) {
    this.index = index;
    this.indexConfig = indexConfig;
    this.tsFormat = new SimpleDateFormat(TIMESTAMP_FORMAT);
    this.tsFormat.setTimeZone(TimeZone.getTimeZone(UTC));
    this.aliasName = (indexConfig.isPerIndexType() ? indexName + "_" + index.typeName : indexName).toLowerCase();
    this.segmentFrame = getSegmentSize();
    this.ttlShift = indexConfig.getTtlShift();
    this.currentName = buildIndexName();

    this.ttlTask = scheduler.scheduleAtFixedRate(new SafeRunnable(this::deleteExpired, "ttl", LOGGER), segmentFrame / 1000,
      segmentFrame / 1000, SECONDS);

    scheduler.scheduleAtFixedRate(new SafeRunnable(this::checkForUpdate, "update", LOGGER), UPDATE_PERIOD, UPDATE_PERIOD, SECONDS);
  }

  @Nonnull
  private String buildIndexName() {
    long now = new Date().getTime();
    long count = now / segmentFrame;

    Date usedDate = new Date(count * segmentFrame);

    return aliasName + INDEX_POSTFIX + tsFormat.format(usedDate);
  }

  @Override
  @Nonnull
  public String getAliasName() {
    return aliasName;
  }

  @Override
  @Nonnull
  public String getCurrentName() {
    return currentName;
  }

  @Override
  public void stop() {
    if (!scheduler.isShutdown()) {
      LOGGER.debug("Canceling CustomIndexManager thread for {}", aliasName);
      scheduler.shutdownNow();
    }
  }

  private long getSegmentSize() {
    long result = DAY;
    String size = indexConfig.getIndexOptions().get(INDEX_SEGMENT_SIZE);
    if (StringUtils.isNotBlank(size)) {
      try {
        result = Long.parseLong(size);
      } catch (NumberFormatException e) {
        LOGGER.error("Incorrect index max size parameter: {}", size);
      }
    }
    return result;
  }

  private long getTimestamp(String name) {
    long result = 0L;
    String strDate = name.substring(name.indexOf("@") + 1);
    if (StringUtils.isNotBlank(strDate)) {
      try {
        Date date = tsFormat.parse(strDate);
        result = date.getTime();
      } catch (ParseException e) {
        LOGGER.error("Incorrect date format in the index name: {}", name);
      }
    }
    return result;
  }

  /**
   * Expired data clean-up. Main concept is: delete an index which does not contain actual data. We
   * extract index creation time from index name for every index in the alias and sort it. If index
   * creation time more than "now" + "ttl" => previously created index does not contain any actual
   * data.
   */
  private void deleteExpired() {
    if (ttlShift > 0) {
      LOGGER.debug("Expiration process started");
      long now = new Date().getTime();
      Map<Long, String> timestamps = index.getIndexNames().stream().collect(Collectors.toMap(this::getTimestamp, e -> e));
      List<Long> ts = timestamps.keySet().stream().sorted().collect(Collectors.toList());
      for (int i = 1; i < ts.size(); i++) {
        if (ts.get(i) + ttlShift * 1000 <= now) {
          String idx = timestamps.get(ts.get(i - 1));
          index.dropIndex(idx);
          LOGGER.debug("Index {} was dropped", idx);
        }
      }
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
    segmentFrame = getSegmentSize();
    ttlShift = indexConfig.getTtlShift();

    if (ttlTask != null) {
      ttlTask.cancel(false);
    }
    ttlTask = scheduler.scheduleAtFixedRate(new SafeRunnable(this::deleteExpired, "ttl", LOGGER), segmentFrame / 1000, segmentFrame / 1000,
      SECONDS);
  }

  @Override
  public boolean isTTLFieldRequired() {
    return false;
  }
}
