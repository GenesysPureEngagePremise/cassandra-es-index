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

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by Jacques-Henri Berthemet on 01/06/2016.
 */
public class QueryMetaDataTest {

  @Test
  public void testWithOptionFalse() {
    QueryMetaData meta = new QueryMetaData("#options:load-rows=false#plop=42");

    Assert.assertEquals("plop=42", meta.query);
    Assert.assertFalse(meta.loadRows());
  }

  @Test
  public void testWithOptionTrue() {
    QueryMetaData meta = new QueryMetaData("#options:load-rows=true#plop=42###");

    Assert.assertEquals("plop=42###", meta.query);
    Assert.assertTrue(meta.loadRows());
  }

  @Test
  public void testWithExtraOptionTrue() {
    QueryMetaData meta = new QueryMetaData("#options:load-rows=true,number=42#plop=42###");

    Assert.assertEquals("plop=42###", meta.query);
    Assert.assertTrue(meta.loadRows());
  }

  @Test
  public void testWithoutOption() {
    QueryMetaData meta = new QueryMetaData("Text=42#plop");

    Assert.assertEquals("Text=42#plop", meta.query);
    Assert.assertTrue(meta.loadRows());
  }

}
