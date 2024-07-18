/*
 * Copyright 2019 Genesys Telecommunications Laboratories, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.genesyslab.webme.commons.index;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.cql3.CQL3Type;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.ReadCommand;
import org.apache.cassandra.db.filter.RowFilter;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.AsciiType;
import org.apache.cassandra.db.marshal.BooleanType;
import org.apache.cassandra.db.marshal.BytesType;
import org.apache.cassandra.db.marshal.CollectionType;
import org.apache.cassandra.db.marshal.CompositeType;
import org.apache.cassandra.db.marshal.CounterColumnType;
import org.apache.cassandra.db.marshal.DateType;
import org.apache.cassandra.db.marshal.DecimalType;
import org.apache.cassandra.db.marshal.DoubleType;
import org.apache.cassandra.db.marshal.EmptyType;
import org.apache.cassandra.db.marshal.FloatType;
import org.apache.cassandra.db.marshal.InetAddressType;
import org.apache.cassandra.db.marshal.Int32Type;
import org.apache.cassandra.db.marshal.IntegerType;
import org.apache.cassandra.db.marshal.LexicalUUIDType;
import org.apache.cassandra.db.marshal.ListType;
import org.apache.cassandra.db.marshal.LongType;
import org.apache.cassandra.db.marshal.MapType;
import org.apache.cassandra.db.marshal.SetType;
import org.apache.cassandra.db.marshal.TimeUUIDType;
import org.apache.cassandra.db.marshal.TimestampType;
import org.apache.cassandra.db.marshal.TupleType;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.db.marshal.UUIDType;
import org.apache.cassandra.db.marshal.UserType;
import org.apache.cassandra.db.rows.Cell;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.gms.FailureDetector;
import org.apache.cassandra.locator.InetAddressAndPort;
import org.apache.cassandra.schema.ColumnMetadata;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.cassandra.serializers.TimestampSerializer;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.Pair;

import com.genesyslab.webme.commons.index.CellElement.CollectionValue;
import com.genesyslab.webme.commons.index.config.IndexConfig;

/**
 * Created by Jacques-Henri Berthemet on 10/24/2014. Some utils to process Cassandra CFs
 */
public class CStarUtils {

  /**
   * Convert a rowKey to a map of column names and put corresponding values in the map. It includes
   * partition keys and clustering columns.
   *
   * @param rowKey can't be null
   * @param tableMetadata can't be null, table metadata, not index metadata
   * @return never null, left is pkName, right is pkValue
   */
  @Nonnull
  static List<Pair<String, String>> getPartitionKeys(@Nonnull ByteBuffer rowKey, @Nonnull TableMetadata tableMetadata)
      throws CharacterCodingException {
    List<Pair<String, String>> partitionKeys = new ArrayList<>(1);

    List<ColumnMetadata> columns = tableMetadata.partitionKeyColumns();
    ColumnMetadata[] pkColDefinitions = columns.toArray(new ColumnMetadata[columns.size()]);

    AbstractType<?> pkValidator = tableMetadata.partitionKeyType;

    // PK is composite we need to extract sub-keys
    if (pkValidator instanceof CompositeType) {
      int pos = 0;
      CompositeType type = (CompositeType) pkValidator;
      for (ByteBuffer key : type.split(rowKey)) {
        String pkName = ByteBufferUtil.string(pkColDefinitions[pos].name.bytes);
        String pkValue = type.types.get(pos).getString(key);

        partitionKeys.add(Pair.create(pkName, pkValue));
        pos++;
      }

    } else { // PK is a single column
      ColumnMetadata pkDefinition = pkColDefinitions[0];
      String pkName = ByteBufferUtil.string(pkDefinition.name.bytes);
      String pkValue = pkValidator.getString(rowKey);

      partitionKeys.add(Pair.create(pkName, pkValue));
    }

    return partitionKeys;
  }

  /**
   * Build a ByteBuffer from ES strings.
   * <p/>
   *
   * @param keys not null, not empty
   */
  @Nonnull
  static ByteBuffer getPartitionKeys(@Nonnull String[] keys, @Nonnull TableMetadata tableMetadata) {
    List<ColumnMetadata> columns = tableMetadata.partitionKeyColumns();
    ColumnMetadata[] pkColDefinitions = columns.toArray(new ColumnMetadata[columns.size()]);

    AbstractType<?> pkValidator = tableMetadata.partitionKeyType;

    // PK is composite we need to extract sub-keys
    if (pkValidator instanceof CompositeType) {
      CompositeType type = (CompositeType) pkValidator;

      Object[] objects = new Object[pkColDefinitions.length];
      int pos = 0;

      for (ColumnMetadata column : columns) {
        if (column.type.asCQL3Type().equals(CQL3Type.Native.INT)) {
          objects[pos] = Integer.valueOf(keys[pos]);
        } else {
          objects[pos] = keys[pos];
        }
        pos++;
      }
      return type.decompose(objects);

    } else { // PK is a single column
      return pkValidator.fromString(keys[0]);
    }
  }

  /**
   * Convert a cell's value to a String according to metadata
   *
   * @param cell not null
   * @return can be null
   */
  @Nullable
  static String cellValueToString(@Nonnull Cell cell) throws IOException {
    if (cell.isLive(FBUtilities.nowInSeconds())) {
      return byteBufferToString(cell.column().type, cell.buffer()).left;
    } else {
      return null;
    }
  }

  /**
   * Convert a cell's (single) value to a String according to AbstractType<br>
   *
   * @param abstractType not null
   * @param value not null
   * @return may be null
   * @throws IOException if type is unknown
   */
  @Nonnull
  private static Pair<String, Boolean> byteBufferToString(@Nonnull AbstractType<?> abstractType, @Nullable ByteBuffer value)
      throws IOException {

    if (value == null) {
      return Pair.create(null, Boolean.FALSE);

    } else if (abstractType instanceof UTF8Type) {

      UTF8Type type = (UTF8Type) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof AsciiType) {

      AsciiType type = (AsciiType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof TimestampType) {
      Date date = TimestampSerializer.instance.deserialize(value);
      return Pair.create(JsonUtils.getIso8601Date(date), Boolean.FALSE);

    } else if (abstractType instanceof DateType) {
      DateType type = (DateType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof UUIDType) {
      UUIDType type = (UUIDType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof LexicalUUIDType) {
      LexicalUUIDType type = (LexicalUUIDType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof TimeUUIDType) {
      TimeUUIDType type = (TimeUUIDType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof DoubleType) {
      DoubleType type = (DoubleType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof FloatType) {
      FloatType type = (FloatType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof InetAddressType) {
      InetAddressType type = (InetAddressType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof DecimalType) {
      DecimalType type = (DecimalType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof Int32Type) {
      Int32Type type = (Int32Type) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof IntegerType) {
      IntegerType type = (IntegerType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof LongType) {
      LongType type = (LongType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof CounterColumnType) {
      CounterColumnType type = (CounterColumnType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof BooleanType) {
      BooleanType type = (BooleanType) abstractType;
      return Pair.create(type.getString(value), Boolean.FALSE);

    } else if (abstractType instanceof UserType) {
      UserType type = (UserType) abstractType;

      Map<String, String> mapValue = new HashMap<>();

      ByteBuffer[] values = type.split(value);
      for (int i = 0; i < values.length; i++) {
        ByteBuffer fieldNameBytes = type.fieldName(i).bytes;
        AbstractType<?> fieldValueType = type.fieldType(i);
        ByteBuffer fieldValueBytes = values[i];

        String fieldName = ByteBufferUtil.string(fieldNameBytes);
        String valueString = byteBufferToString(fieldValueType, fieldValueBytes).left;

        mapValue.put(fieldName, valueString);
      }

      return Pair.create(JsonUtils.stringMapToJson(mapValue), Boolean.TRUE);

    } else if (abstractType instanceof TupleType) {
      TupleType type = (TupleType) abstractType;
      ByteBuffer[] values = type.split(value);

      List<String> arrayList = new ArrayList<>(values.length);

      for (int i = 0; i < values.length; i++) {
        AbstractType<?> tupleValueType = type.type(i);
        arrayList.add(byteBufferToString(tupleValueType, values[i]).left);
      }

      return Pair.create(JsonUtils.collectionToArray(arrayList), Boolean.TRUE);

    } else if (abstractType instanceof MapType) {
      MapType<?, ?> type = (MapType<?, ?>) abstractType;
      AbstractType<?> valueType = type.getValuesType();
      return byteBufferToString(valueType, value);

    } else if (abstractType instanceof SetType) {
      SetType<?> type = (SetType<?>) abstractType;
      AbstractType<?> valueType = type.valueComparator();
      return byteBufferToString(valueType, value);

    } else if (abstractType instanceof ListType) {
      ListType<?> type = (ListType<?>) abstractType;
      AbstractType<?> valueType = type.valueComparator();
      return byteBufferToString(valueType, value);

    } else if (abstractType instanceof BytesType) {
      return Pair.create(value.remaining() + " bytes", Boolean.FALSE);

    } else if (abstractType instanceof EmptyType) {
      return Pair.create("", Boolean.FALSE);
    }

    throw new IOException("Unsupported type:" + abstractType);
  }

  /**
   * Get collection element from a cell
   *
   * @param cell not null
   * @return a CollectionValue
   */
  @Nonnull
  static CollectionValue getCollectionElement(@Nonnull Cell cell) throws IOException {
    final CollectionValue.CollectionType colType;
    final String key;

    final AbstractType<?> abstractType = cell.column().type;

    if (abstractType instanceof MapType) {
      colType = CollectionValue.CollectionType.MAP;
      AbstractType keyType = ((MapType) abstractType).getKeysType();
      key = byteBufferToString(keyType, cell.path().get(0)).left; // cell path contains map key name

    } else if (abstractType instanceof SetType) {
      colType = CollectionValue.CollectionType.SET;
      key = ((SetType) abstractType).nameComparator().getString(cell.path().get(0)); // cell path contains set item value

    } else if (abstractType instanceof ListType) {
      colType = CollectionValue.CollectionType.LIST;
      key = ((ListType) abstractType).nameComparator().getString(cell.path().get(0)); // cell path contains list item value

    } else {
      throw new IOException("Unsupported Collection type:" + abstractType);
    }

    if (cell.isLive(FBUtilities.nowInSeconds())) { // isLive() is better than isTombstone in case of commitlog replay or hints
      Pair<String, Boolean> pair = byteBufferToString(abstractType, cell.buffer());
      if (pair.right) {
        return CollectionValue.create(key, pair.left, CollectionValue.CollectionType.JSON);
      } else {
        return CollectionValue.create(key, pair.left, colType);
      }
    } else {
      return CollectionValue.create(key, null, colType);
    }
  }

  /**
   * Is Cell's validator an instanceof CollectionType
   *
   * @param cell can be null, returns false
   * @return cell validator instanceof CollectionType
   */
  static boolean isCollection(@Nullable Cell cell) {
    return cell != null && cell.column().type instanceof CollectionType;
  }

  /**
   * Convert a list of PK + CK to a single line id <br>
   * PK-PK-CK-CK-CK
   *
   * @param partitionKeys not null
   * @param clusteringKeys can be null
   * @return null if map is empty
   */
  @Nullable
  static String toEsId(@Nonnull List<Pair<String, String>> partitionKeys, @Nullable List<Pair<String, String>> clusteringKeys) {

    if (partitionKeys.size() == 0) {
      return null;
    } else if (partitionKeys.size() == 1 && (clusteringKeys == null || clusteringKeys.isEmpty())) {
      return partitionKeys.get(0).right;
    } else {
      StringBuilder primaryKeyBuilder = new StringBuilder();

      // Aggregate partition keys
      addKeys(partitionKeys.iterator(), primaryKeyBuilder);

      // Now clusteringKeys if exists
      if (clusteringKeys != null && !clusteringKeys.isEmpty()) {
        primaryKeyBuilder.append(IndexConfig.ES_ID_SEPARATOR); // append separator for next key
        addKeys(clusteringKeys.iterator(), primaryKeyBuilder);
      }

      return primaryKeyBuilder.toString();
    }
  }

  private static void addKeys(Iterator<Pair<String, String>> keyIterator, StringBuilder primaryKeyBuilder) {
    while (keyIterator.hasNext()) {
      Pair<String, String> pair = keyIterator.next();
      primaryKeyBuilder.append(pair.right);
      if (keyIterator.hasNext()) {
        primaryKeyBuilder.append(IndexConfig.ES_ID_SEPARATOR);
      }
    }
  }

  /**
   * Get partition key names
   *
   * @param metadata table metadata
   * @return Partition keys
   */
  @Nonnull
  static List<String> getPartitionKeyNames(@Nonnull TableMetadata metadata) throws CharacterCodingException {
    List<ColumnMetadata> partitionKeys = metadata.partitionKeyColumns();
    List<String> primaryKeys = new ArrayList<>(partitionKeys.size());

    for (ColumnMetadata colDef : partitionKeys) {
      String keyName = ByteBufferUtil.string(colDef.name.bytes);
      primaryKeys.add(keyName);
    }

    return primaryKeys;
  }

  /**
   * Get clustering keys
   *
   * @param metadata table metadata
   * @return Clustering keys, can be empty
   */
  @Nonnull
  static List<String> getClusteringColumnsNames(@Nonnull TableMetadata metadata) throws CharacterCodingException {
    List<ColumnMetadata> clusteringColumns = metadata.clusteringColumns();
    List<String> clusteringColumnsNames = new ArrayList<>(clusteringColumns.size());

    for (ColumnMetadata colDef : clusteringColumns) {
      String keyName = ByteBufferUtil.string(colDef.name.bytes);
      clusteringColumnsNames.add(keyName);
    }

    return clusteringColumnsNames;
  }

  /**
   * Retrieve ClusteringKeys value from cell name
   *
   * @param row not null
   * @param tableMetadata not null
   * @param clusteringColumnsNames can be null
   * @return null if ColumnFamily has no collections, a list else
   */
  @Nullable
  static List<Pair<String, String>> getClusteringKeys(@Nonnull Row row, @Nonnull TableMetadata tableMetadata,
      @Nonnull List<String> clusteringColumnsNames) {
    int clusteringPrefixSize = row.clustering().size();
    if (clusteringPrefixSize > 0) {
      List<Pair<String, String>> keys = new ArrayList<>(clusteringPrefixSize);

      for (int prefixNb = 0; prefixNb < clusteringPrefixSize; prefixNb++) {
        String name = clusteringColumnsNames.get(prefixNb);
        AbstractType<?> subtype = tableMetadata.comparator.subtype(prefixNb);
        ByteBuffer clusteringKeyBytes = row.clustering().bufferAt(prefixNb);
        String value = subtype.getString(clusteringKeyBytes);
        keys.add(Pair.create(name, value));
      }
      return keys;
    } else {
      return null;
    }
  }

  @Nonnull
  static String queryString(@Nonnull ReadCommand command) {
    RowFilter filter = command.rowFilter();
    List<RowFilter.Expression> clause = filter.getExpressions();
    RowFilter.Expression expression = clause.isEmpty() ? null : clause.get(0);
    if (expression == null) {
      throw new InvalidRequestException("Missing clause:" + filter);
    }

    try {
      return ByteBufferUtil.string(expression.getIndexValue(), UTF_8);
    } catch (CharacterCodingException e) {
      throw new InvalidRequestException(e.getMessage());
    }
  }

  static boolean isOwner(@Nonnull ColumnFamilyStore cfs, @Nonnull Token token) {
    // Get all live endpoints which was selected to replicate this data
    List<InetAddressAndPort> addresses = cfs.keyspace.getReplicationStrategy().getNaturalReplicasForToken(token).endpointList();
    Map<String, InetAddressAndPort> indexers = new HashMap<>();

    // Build DC-based map - select only single (first) node to index, because getLiveNaturalEndpoints
    // returns same values for all nodes
    for (InetAddressAndPort address : addresses) {
      if (FailureDetector.instance.isAlive(address)) {
        String datacenter = DatabaseDescriptor.getEndpointSnitch().getDatacenter(address);
        if (!indexers.containsKey(datacenter)) {
          indexers.put(datacenter, address);
        }
      }
    }
    return indexers.containsValue(FBUtilities.getBroadcastAddressAndPort()); // Current node is not indexer (not first)
  }

  public static String getLocalDC() {
    return DatabaseDescriptor.getEndpointSnitch().getDatacenter(FBUtilities.getBroadcastAddressAndPort());
  }

  public static List<String> getDCs() {
    Set<InetAddressAndPort> addresses = StorageService.instance.getTokenMetadata().getAllEndpoints();
    return addresses.stream().map(address -> DatabaseDescriptor.getEndpointSnitch().getDatacenter(address)).distinct()
        .collect(Collectors.toList());
  }
}
