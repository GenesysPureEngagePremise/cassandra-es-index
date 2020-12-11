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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import io.searchbox.client.JestResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Predicate;

import static org.codehaus.jackson.JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS;
import static org.codehaus.jackson.JsonParser.Feature.ALLOW_SINGLE_QUOTES;

/**
 * Created by Jacques-Henri Berthemet on 05/07/2017.
 */
public class JsonUtils {

  private static final String EXTENDED_ISO8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
  private static final TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");

  private static final ObjectMapper OBJECT_MAPPER;
  private static final JsonFactory JSON_FACTORY = new JsonFactory();
  private static final JsonParser GSON_PARSER = new JsonParser(); // com.google.gson.JsonParser

  static {
    OBJECT_MAPPER = new ObjectMapper().configure(ALLOW_SINGLE_QUOTES, true);
    OBJECT_MAPPER.getJsonFactory().enable(ALLOW_NON_NUMERIC_NUMBERS);
  }

  @Nonnull
  static Map<String, String> jsonStringToStringMap(@Nonnull String jsonString) throws IOException {
    return OBJECT_MAPPER.readValue(jsonString, new TypeReference<HashMap<String, String>>() {
    });
  }

  @Nonnull
  private static Map<String, Object> jsonStringToObjectMap(@Nonnull String jsonString) throws IOException {
    return OBJECT_MAPPER.readValue(jsonString, new TypeReference<Map<String, Object>>() {
    });
  }

  @Nonnull
  public static JsonObject asJsonObject(@Nonnull String jsonString) {
    return GSON_PARSER.parse(jsonString).getAsJsonObject();
  }

  /**
   * Transform the JSON to a map:string,string[] because ES won't support values of different types for the same key
   */
  @Nonnull
  static String flatten(@Nonnull String jsonString) throws IOException {
    StringWriter stringWriter = new StringWriter();
    JsonGenerator builder = JSON_FACTORY.createJsonGenerator(stringWriter);

    builder.writeStartObject();

    for (Map.Entry<String, Object> entry : jsonStringToObjectMap(jsonString).entrySet()) {
      Object value = entry.getValue();
      builder.writeFieldName(entry.getKey());
      builder.writeStartArray();

      //sub-maps are transformed in arrays of key-values
      //this allows searching for NAME:key=value
      if (value instanceof Map<?, ?>) {
        for (Map.Entry<?, ?> subEntry : ((Map<?, ?>) value).entrySet()) {
          builder.writeString(String.format("%s=%s", String.valueOf(subEntry.getKey()), String.valueOf(subEntry.getValue())));
        }
      } else if (value instanceof Object[]) { //arrays to arrays of string
        for (Object object : ((Object[]) value)) {
          builder.writeString(String.valueOf(object));
        }
      } else if (value instanceof Collection) { //Collections to arrays of string
        for (Object object : ((Collection<?>) value)) {
          builder.writeString(String.valueOf(object));
        }
      } else { //single values in their string representations
        builder.writeString(String.valueOf(value));
      }
      builder.writeEndArray();
    }

    builder.writeEndObject();
    builder.close();

    return stringWriter.toString();
  }

  /**
   * 2016-01-05T13:49:25.143Z
   */
  @Nonnull
  static String getIso8601Date(@Nonnull Date date) {
    SimpleDateFormat dateFormat = new SimpleDateFormat(EXTENDED_ISO8601_FORMAT);
    dateFormat.setTimeZone(GMT_ZONE);
    return dateFormat.format(date);
  }

  @Nonnull
  static String stringMapToJson(@Nonnull Map<String, String> mapValue) throws IOException {
    StringWriter stringWriter = new StringWriter();
    JsonGenerator builder = JSON_FACTORY.createJsonGenerator(stringWriter);

    builder.writeStartObject();

    for (Map.Entry<String, String> entry : mapValue.entrySet()) {
      builder.writeStringField(entry.getKey(), entry.getValue());
    }

    builder.writeEndObject();
    builder.close();

    return stringWriter.toString();
  }

  @Nonnull
  static String collectionToArray(@Nonnull Collection<String> collection) throws IOException {
    StringWriter stringWriter = new StringWriter();
    JsonGenerator builder = JSON_FACTORY.createJsonGenerator(stringWriter);

    builder.writeStartArray();

    for (String entry : collection) {
      builder.writeString(entry);
    }

    builder.writeEndArray();
    builder.close();

    return stringWriter.toString();
  }

  /**
   * @param result JestResult to extract
   * @param keys   the key chain to go through
   * @return an empty JsonObject if key chain not found
   */
  @Nonnull
  public static JsonObject getJsonObject(@Nonnull JestResult result, @Nonnull String... keys) {
    JsonObject object = result.getJsonObject();

    for (String key : keys) {
      if (object == null) {
        return new JsonObject();
      } else {
        JsonElement subObject = object.get(key);
        object = subObject == null ? null : subObject.getAsJsonObject();
      }
    }

    return object == null ? new JsonObject() : object;
  }

  @Nullable
  public static String getString(@Nullable JsonElement element, @Nonnull String... path) {
    if (element == null) {
      return null;
    }
    for (int i = 0; i < path.length; i++) {
      String key = path[i];
      if (i + 1 == path.length) {
        JsonElement value = element.getAsJsonObject().get(key);
        return value == null ? null : value.getAsString();
      } else if (element.isJsonObject()) {
        element = element.getAsJsonObject().get(key);
        if (element == null || !element.isJsonObject()) {
          return null;
        }
      } else {
        return null;
      }
    }
    return null;
  }

  /**
   * @param jsonObject object to filter
   * @param keys       keys to remove
   * @return a copy as deep as key.length
   */
  @Nonnull
  static JsonObject filterKeys(@Nonnull JsonObject jsonObject, @Nonnull String... keys) {
    JsonObject filtered = jsonObject;
    for (String key : keys) {
      filtered = filterPath(filtered, key);
    }
    return filtered;
  }

  /**
   * @param jsonObject object to filter
   * @param path       path to the key to remove
   * @return a copy as deep as key.length
   */
  @Nonnull
  static JsonObject filterPath(@Nonnull JsonObject jsonObject, @Nonnull String... path) {
    if (path.length == 0) {
      return jsonObject;

    } else {
      JsonObject result = new JsonObject();

      jsonObject.entrySet().forEach(e -> {
        String key = e.getKey();
        JsonElement value = e.getValue();
        if (path[0].equals(e.getKey())) {
          if (path.length > 1) { // if length == 1, this is the key to remove
            if (value instanceof JsonObject) { // if value is an object, filter further
              value = JsonUtils.filterPath((JsonObject) value, Arrays.copyOfRange(path, 1, path.length));
            }
            result.add(key, value);
          }
        } else {
          result.add(key, value);
        }
      });
      return result;
    }
  }

  /**
   * @param jsonObject object to filter
   * @param predicate  matching predicate will keep the keys
   * @return a shallow copy
   */
  @Nonnull
  public static JsonObject filter(@Nonnull JsonObject jsonObject, @Nonnull Predicate<String> predicate) {
    JsonObject res = new JsonObject();

    jsonObject.entrySet().forEach(e -> {
      if (predicate.test(e.getKey())) {
        res.add(e.getKey(), e.getValue());
      }
    });

    return res;
  }


  @Nonnull
  static String unQuote(@Nonnull String string) {
    return string.replaceAll("\"", "");
  }

  /**
   * @param main  elements of this json will overwrite the other's params
   * @param other will be overwritten by main if same keys exists
   * @return null if both are null, if one is null the other is returned
   */
  @Nullable
  public static JsonObject mergeJson(@Nullable JsonObject main, @Nullable JsonObject other) {
    if (other == null) {
      return main;
    } else if (main == null) {
      return other;
    }

    JsonObject merged = new JsonObject();
    other.entrySet().forEach(elem -> merged.add(elem.getKey(), elem.getValue()));
    main.entrySet().forEach(elem -> merged.add(elem.getKey(), elem.getValue()));

    return merged;
  }

  static Long getLong(@Nonnull JsonElement element, @Nonnull String name) {
    String value = getString(element, name);
    try {
      if (value != null) {
        return Long.valueOf(value);
      }
    } catch (NumberFormatException e) {
      return null;
    }
    return null;
  }

  public static JsonObject dotedToStructured(JsonObject src) {
    JsonObject dest = new JsonObject();
    src.entrySet().forEach(item -> {
      JsonObject node = dest;

      for (Iterator<String> it = Arrays.stream(item.getKey().split("\\.")).iterator(); it.hasNext(); ) {
        String key = it.next();

        if (!it.hasNext()) {
          node.add(key, item.getValue());
        } else {
          if (!node.has(key)) {
            node.add(key, new JsonObject());
          }
          node = node.getAsJsonObject(key);
        }
      }
    });
    return dest;
  }
}
