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

import org.slf4j.Logger;

import javax.annotation.Nonnull;

/**
 * @author Jacques-Henri Berthemet 13/09/2017
 */
public class SafeRunnable implements Runnable {

  private final Runnable runnable;
  private final String name;
  private final Logger logger;

  public SafeRunnable(@Nonnull Runnable runnable, @Nonnull String name, @Nonnull Logger logger) {
    this.runnable = runnable;
    this.name = name;
    this.logger = logger;
  }

  @Override
  public void run() {
    try {
      runnable.run();
    } catch (Throwable ex) {
      logger.error("Operation '{}' failed: {}", name, ex.getMessage(), ex);
    }
  }
}
