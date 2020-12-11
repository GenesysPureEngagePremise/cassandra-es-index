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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static com.genesyslab.webme.commons.index.JsonUtils.dotedToStructured;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * Created by Jacques-Henri Berthemet on 05/07/2017.
 */
public class JsonUtilsTest {

  private final JsonObject obj = new JsonObject();

  @Before
  public void makeJsonObject() {
    obj.addProperty("keep", "1");
    obj.addProperty("preserve", "2");
    JsonObject inner1 = new JsonObject();
    inner1.addProperty("remove me", "3");
    inner1.addProperty("keep me", "4");
    obj.add("Inner1", inner1);
    JsonObject inner2 = new JsonObject();
    inner2.addProperty("remove me", "5");
    inner2.addProperty("keep me", "6");
    obj.add("Inner2", inner2);
  }

  @Test
  public void jsonStringToStringMapTest() throws IOException {
    assertEquals("{doors=5, brand=Mercedes}", JsonUtils.jsonStringToStringMap("{ \"brand\" : \"Mercedes\", \"doors\" : 5 }").toString());
  }

  @Test
  public void testPredicate() {
    JsonObject obj = new JsonObject();
    obj.addProperty("<filtered", 0);
    obj.addProperty("notFiltered", 0);
    assertEquals("{\"notFiltered\":0}", JsonUtils.filter(obj, k -> !k.startsWith("<")).toString());
  }

  @Test
  public void filterShouldRemoveKey() {
    assertThat(JsonUtils.filterKeys(obj, "Inner1").toString(),
      is("{\"keep\":\"1\",\"preserve\":\"2\","
        + "\"Inner2\":{\"remove me\":\"5\",\"keep me\":\"6\"}}"));
  }

  @Test
  public void filterShouldRemoveInnerKeys() {
    assertThat(JsonUtils.filterPath(obj, "Inner1", "remove me").toString(),
      is("{\"keep\":\"1\",\"preserve\":\"2\","
        + "\"Inner1\":{\"keep me\":\"4\"},"
        + "\"Inner2\":{\"remove me\":\"5\",\"keep me\":\"6\"}}"));
  }

  @Test
  public void getStringShouldReturnExpectedValue() {
    assertThat(JsonUtils.getString(obj, "keep"), is("1"));
    assertThat(JsonUtils.getString(obj, "Inner1", "keep me"), is("4"));
    assertThat(JsonUtils.getString(obj, "Inner2", "remove me"), is("5"));
  }

  String SRC =
    "{\"index.translog.durability\":\"async\",\"analysis.analyzer.email_analyzer.filter\":\"lowercase\",\"analysis.analyzer.html_analyzer.tokenizer\":\"ngram\",\"analysis.analyzer.email_analyzer.type\":\"pattern\",\"index.analysis.normalizer.lower_ascii_normalizer.filter\":[\"lowercase\",\"asciifolding\"],\"index.analysis.analyzer.lowercase_analyzer.filter\":\"lowercase\",\"index.analysis.analyzer.lowercase_analyzer.type\":\"custom\",\"analysis.analyzer.html_analyzer.type\":\"custom\",\"analysis.analyzer.html_analyzer.filter\":\"lowercase\",\"analysis.analyzer.html_analyzer.char_filter\":\"html_strip\",\"index.analysis.normalizer.lower_ascii_normalizer.type\":\"custom\",\"index.analysis.analyzer.lowercase_analyzer.tokenizer\":\"keyword\"}";
  String EXP =
    "{\"index\":{\"translog\":{\"durability\":\"async\"},\"analysis\":{\"normalizer\":{\"lower_ascii_normalizer\":{\"filter\":[\"lowercase\",\"asciifolding\"],\"type\":\"custom\"}},\"analyzer\":{\"lowercase_analyzer\":{\"filter\":\"lowercase\",\"type\":\"custom\",\"tokenizer\":\"keyword\"}}}},\"analysis\":{\"analyzer\":{\"email_analyzer\":{\"filter\":\"lowercase\",\"type\":\"pattern\"},\"html_analyzer\":{\"tokenizer\":\"ngram\",\"type\":\"custom\",\"filter\":\"lowercase\",\"char_filter\":\"html_strip\"}}}}";

  @Test
  public void dotedToStructuredTest() throws IOException {

    assertEquals(EXP, dotedToStructured((JsonObject) new JsonParser().parse(SRC)).toString());
  }

}
