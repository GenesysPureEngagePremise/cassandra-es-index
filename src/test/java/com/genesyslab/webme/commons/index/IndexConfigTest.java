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
import com.genesyslab.webme.commons.index.config.IndexConfiguration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Jacques-Henri Berthemet 23/10/2018
 */
public class IndexConfigTest {

  @Before
  @After
  public void setup() {
    EsSecondaryIndexUnderTest.staticInit();
    System.setProperty("genesys-es-unicast-hosts", "");
    System.setProperty("genesys-es-<datacenter1.rack1>.unicast-hosts", "");
  }

  @Test
  public void testNoOptions() {
    IndexConfig indexConfig = new IndexConfiguration("Test", new HashMap<>());
    assertThat(indexConfig.getUnicastHosts(), is(nullValue()));
  }


  @Test
  public void testDcOptions() {
    {
      Map<String, String> options = new HashMap<>();
      options.put("<datacenter1.rack1>.unicast-hosts", "mars");
      IndexConfig indexConfig = new IndexConfiguration("Test", options);
      assertThat(indexConfig.getUnicastHosts(), is("mars"));
    }

    {
      Map<String, String> options = new HashMap<>();
      options.put("<datacenter1>.unicast-hosts", "jupiter");
      IndexConfig indexConfig = new IndexConfiguration("Test", options);
      assertThat(indexConfig.getUnicastHosts(), is("jupiter"));
    }

    {
      Map<String, String> options = new HashMap<>();
      options.put("unicast-hosts", "pluto");
      IndexConfig indexConfig = new IndexConfiguration("Test", options);
      assertThat(indexConfig.getUnicastHosts(), is("pluto"));
    }
  }
}
