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

import javax.annotation.Nonnull;

/**
 * Handles changes in index names, updates and scheduled operations
 *
 * @author Jacques-Henri Berthemet 04/09/2017
 */
public interface IndexManager {
  /**
   * Alias name
   * 
   * @return alias name
   */
  @Nonnull
  String getAliasName();

  /**
   * Current index name
   * 
   * @return current (active) index name
   */
  @Nonnull
  String getCurrentName();

  /**
   * Stop index processing
   */
  void stop();

  /**
   * Check index segmentation processing
   */
  void checkForUpdate();

  /**
   * Reload index manager options
   */
  void updateOptions();

  /**
   * Is separated TTL field must be created in every document
   */
  boolean isTTLFieldRequired();
}
