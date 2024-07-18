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

import static com.genesyslab.webme.commons.index.DefaultIndexManager.INDEX_POSTFIX;
import static com.genesyslab.webme.commons.index.JsonUtils.unQuote;
import static com.genesyslab.webme.commons.index.config.IndexConfig.ES_CONFIG_PREFIX;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.Operator;
import org.apache.cassandra.cql3.statements.schema.IndexTarget;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.EmptyIterators;
import org.apache.cassandra.db.PartitionRangeReadCommand;
import org.apache.cassandra.db.ReadCommand;
import org.apache.cassandra.db.RegularAndStaticColumns;
import org.apache.cassandra.db.WriteContext;
import org.apache.cassandra.db.compaction.OperationType;
import org.apache.cassandra.db.filter.EsSimpleExpression;
import org.apache.cassandra.db.filter.RowFilter;
import org.apache.cassandra.db.filter.RowFilter.Expression;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.partitions.PartitionIterator;
import org.apache.cassandra.db.partitions.PartitionUpdate;
import org.apache.cassandra.db.partitions.UnfilteredPartitionIterator;
import org.apache.cassandra.db.rows.Cell;
import org.apache.cassandra.db.rows.Row;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.exceptions.InvalidRequestException;
import org.apache.cassandra.index.Index;
import org.apache.cassandra.index.IndexRegistry;
import org.apache.cassandra.index.transactions.IndexTransaction;
import org.apache.cassandra.io.sstable.Descriptor;
import org.apache.cassandra.io.sstable.format.SSTableFlushObserver;
import org.apache.cassandra.schema.ColumnMetadata;
import org.apache.cassandra.schema.IndexMetadata;
import org.apache.cassandra.schema.MigrationManager;
import org.apache.cassandra.schema.TableMetadata;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.tracing.Tracing;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.apache.cassandra.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.genesyslab.webme.commons.index.config.IndexConfig;
import com.genesyslab.webme.commons.index.config.IndexConfiguration;
import com.genesyslab.webme.commons.index.config.LogConfigurator;
import com.genesyslab.webme.commons.index.indexers.EsIndexer;
import com.genesyslab.webme.commons.index.indexers.FakePartitionIterator;
import com.genesyslab.webme.commons.index.indexers.NoOpIndexer;
import com.genesyslab.webme.commons.index.indexers.NoOpPartitionIterator;
import com.genesyslab.webme.commons.index.indexers.StreamingPartitionIterator;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.gson.JsonObject;

/**
 * Interesting read to adapt to Cassandra 3: http://www.doanduyhai.com/blog/?p=2058
 * <p>
 * Created by Jacques-Henri Berthemet on 10/24/2014.
 */
public class EsSecondaryIndex implements Index {

  public static final boolean DEBUG_SHOW_VALUES = Boolean.getBoolean(ES_CONFIG_PREFIX + "show-values");
  /**
   * if this option is true it allows Cassandra to start even if index is not properly initialized
   */
  static final boolean START_WITH_FAILED_INDEX = Boolean.getBoolean(ES_CONFIG_PREFIX + "force-start");

  private static final Logger LOGGER = LoggerFactory.getLogger(EsSecondaryIndex.class);

  private static final String UPDATE = "#update#";
  private static final String GET_MAPPING = "#get_mapping#";
  private static final String PUT_MAPPING = "#put_mapping#";
  private static final String FAKE_ID = "FakeId";

  private static final boolean DEPENDS_ON_COLUMN_DEFINITION = false; // we support dynamic addition/removal of columns

  public final ColumnFamilyStore baseCfs;
  public final String indexColumnName;
  public final String name;
  final IndexConfig indexConfig;
  @Nonnull
  final IndexInterface esIndex;
  private final SecureRandom random = new SecureRandom();
  private final ColumnMetadata indexColDef;
  private final boolean isDummyMode;
  private IndexMetadata indexMetadata;
  private List<String> partitionKeysNames;
  private List<String> clusteringColumnsNames;
  private boolean hasClusteringColumns;
  private ConsistencyLevel readConsistencyLevel;
  private boolean skipLogReplay;
  private boolean skipNonLocalUpdates;
  private boolean discardNullValues;
  private boolean analyticMode;
  private boolean indexAvailableWhenBuilding;

  public EsSecondaryIndex(ColumnFamilyStore sourceCfs, IndexMetadata indexMetadata) throws Exception {
    synchronized (EsSecondaryIndex.class) { // we create one index at a time
      LogConfigurator.configure(); // disable the very verbose Apache client logs

      this.baseCfs = sourceCfs;
      this.indexMetadata = indexMetadata;
      indexColumnName = unQuote(this.indexMetadata.options.get(IndexTarget.TARGET_OPTION_NAME));
      indexColDef = baseCfs.metadata().getColumn(ColumnIdentifier.getInterned(indexColumnName, true));
      name = "EsSecondaryIndex [" + baseCfs.metadata.keyspace + "." + this.indexMetadata.name + "]";
      indexConfig = new IndexConfiguration(name, indexMetadata.options);

      LOGGER.info("Creating {} with options {}", name, indexConfig.getIndexOptions());

      IndexInterface index;

      try {
        if (indexConfig.isDummyMode()) {
          LOGGER.warn("{} dummy mode enabled", name);
          index = new EsDummyIndex();
        } else {
          LOGGER.warn("EsSecondaryIndex {} initializing #{}", name, Integer.toHexString(hashCode()));
          partitionKeysNames = Collections.unmodifiableList(CStarUtils.getPartitionKeyNames(baseCfs.metadata()));
          clusteringColumnsNames = Collections.unmodifiableList(CStarUtils.getClusteringColumnsNames(baseCfs.metadata()));

          LOGGER.debug("ReadConsistencyLevel is '{}', skip startup log replay:{}, skip non local updates:{}", readConsistencyLevel,
              skipLogReplay, skipNonLocalUpdates);

          hasClusteringColumns = !clusteringColumnsNames.isEmpty();
          index = new ElasticIndex(indexConfig, baseCfs.metadata.keyspace, baseCfs.name, partitionKeysNames, clusteringColumnsNames);
          index.init();
          LOGGER.warn("Initialized {} ", name);
        }
      } catch (Exception ex) {
        if (START_WITH_FAILED_INDEX) {
          index = new EsDummyIndex();
          LOGGER.error("Index {} initialization failed, starting in dummy mode", name, ex);
        } else {
          throw ex;
        }
      }

      isDummyMode = index instanceof EsDummyIndex;
      esIndex = index;
      updateIndexConfigOptions();
    }
  }

  private void updateIndexConfigOptions() {
    readConsistencyLevel = indexConfig.getReadConsistencyLevel();
    skipLogReplay = indexConfig.isSkipLogReplay();
    skipNonLocalUpdates = indexConfig.isSkipNonLocalUpdates();
    discardNullValues = indexConfig.isDiscardNullValues();
    analyticMode = indexConfig.isAnalyticMode();
    indexAvailableWhenBuilding = indexConfig.isIndexAvailableWhenBuilding();
    esIndex.updateIndexConfigOptions();
  }

  /**
   * @param decoratedKey PK of the update
   * @param newRow the new version of the row
   * @param oldRow provided in case of updates, null for inserts
   * @param nowInSec time of the update
   */
  public void index(@Nonnull DecoratedKey decoratedKey, @Nonnull Row newRow, @Nullable Row oldRow, int nowInSec) {

    String id = ByteBufferUtil.bytesToHex(decoratedKey.getKey());
    Tracing.trace("ESI decoding row {}", id); // This is CQL "tracing on" support

    try {
      List<Pair<String, String>> partitionKeys = CStarUtils.getPartitionKeys(decoratedKey.getKey(), baseCfs.metadata());
      List<CellElement> elements = new ArrayList<>();

      for (Cell cell : newRow.cells()) {
        if (cell.isLive(nowInSec) || !discardNullValues) { // optionally ignore null values

          // Skip the cells with empty name (row marker) // looks like isEmpty() now (2.2?) returns false with
          // empty string
          String cellName = cell.column().name.toString();

          if (!Strings.isNullOrEmpty(cellName)) { // Skip the cells with empty name (row marker)
            CellElement element = new CellElement();
            element.name = cellName;

            if (hasClusteringColumns) {
              element.clusteringKeys = CStarUtils.getClusteringKeys(newRow, baseCfs.metadata(), clusteringColumnsNames);
            }

            if (CStarUtils.isCollection(cell)) {
              element.collectionValue = CStarUtils.getCollectionElement(cell);
            } else {
              element.value = CStarUtils.cellValueToString(cell);
            }
            elements.add(element);
          }
        }
      }

      if (elements.isEmpty()) {
        Tracing.trace("ESI skip empty update {} done", id);
        LOGGER.debug("{} skip empty update for row {}", name, id);

      } else {
        if (DEBUG_SHOW_VALUES) {
          LOGGER.debug("{} indexing row {} content:{}", name, id, newRow);
        } else {
          LOGGER.debug("{} indexing row {}", name, id);
        }

        Tracing.trace("ESI writing {} to ES index", id);
        esIndex.index(partitionKeys, elements, newRow.primaryKeyLivenessInfo().localExpirationTime(), oldRow == null);
        Tracing.trace("ESI index {} done", id);
      }

    } catch (Exception e) {
      LOGGER.error("{} can't index row {} {}", name, id, e);
      Tracing.trace("ESI error: can't index row {} {}", id, e.getMessage());

      throw new RuntimeException(e);
    }
  }

  public void delete(DecoratedKey decoratedKey) {
    String id = ByteBufferUtil.bytesToHex(decoratedKey.getKey());

    try {
      Token token = decoratedKey.getToken();
      if (CStarUtils.isOwner(baseCfs, token)) {
        LOGGER.trace("{} deleting {}", name, id);
      } else {
        LOGGER.trace("{} skipping {} because {} is not in our range", name, id, token);
        return;
      }

      esIndex.delete(CStarUtils.getPartitionKeys(decoratedKey.getKey(), baseCfs.metadata()));
    } catch (Exception e) {
      LOGGER.error("{} can't delete row {} {}", name, id, e);
      throw new RuntimeException(e);
    }
  }

  boolean reloadSettings() {
    boolean changed = indexConfig.reload(indexMetadata.options);
    if (changed) {
      updateIndexConfigOptions();
    }
    return changed;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public IndexBuildingSupport getBuildTaskSupport() { // This is for rebuild command
    if (isDummyMode) {
      return null;
    }
    return (cfs, indexes, ssTables) -> new EsIndexBuilder(EsSecondaryIndex.this, ssTables);
  }

  @Override
  public Callable<?> getInitializationTask() { // This is done when starting Cassandra or when creating an index with CQL command
    return () -> {
      if (esIndex.isNewIndex()) { // FIXME will this rebuild all data since we only have ssTables for our replicas?
        LOGGER.info("{} index building", name);
        new EsIndexBuilder(EsSecondaryIndex.this).build();
        LOGGER.info("{} index build completed", name);
      } else {
        LOGGER.debug("{} already exists, nothing to rebuild", name);
      }
      return null;
    };
  }

  @Override
  public IndexMetadata getIndexMetadata() {
    return indexMetadata;
  }

  @Override
  public Callable<?> getMetadataReloadTask(IndexMetadata indexMetadata) {
    return this::reloadSettings;
  }

  @Override
  public void register(IndexRegistry registry) {
    LOGGER.info("Registering {} against {}", name, registry);
    registry.registerIndex(this);
  }

  @Override
  public Optional<ColumnFamilyStore> getBackingTable() {
    return Optional.empty(); // We don't use a CFS to store index data
  }

  @Override
  public Callable<?> getBlockingFlushTask() {
    return esIndex::flush;
  }

  @Override
  public Callable<?> getInvalidateTask() {
    return esIndex::drop;
  }

  @Override
  public Callable<?> getTruncateTask(long truncatedAt) {
    return esIndex::truncate;
  }

  @Override
  public Callable<?> getPreJoinTask(boolean hadBootstrap) {
    return () -> null;
  }

  @Override
  public boolean shouldBuildBlocking() {
    return false; // We don't want to block table/index access while it's (re)building
  }

  @Override
  public SSTableFlushObserver getFlushObserver(Descriptor descriptor, OperationType opType) {
    return null; // Don't think we care about table flushes
  }

  @Override
  public boolean dependsOn(ColumnMetadata column) {
    return DEPENDS_ON_COLUMN_DEFINITION;
  }

  @Override
  public boolean supportsExpression(ColumnMetadata column, Operator operator) {
     // We support any kind of C* expressions because it's actually in the ES query.
     // however check that column is indexed (called from SecondaryIndexManager.getBestIndexFor())
    return this.indexColDef != null && this.indexColDef.name.equals(column.name);
  }

  @Override
  public AbstractType<?> customExpressionValueType() {
    return null; // we don't support custom expressions, yet.
  }

  @Override
  public RowFilter getPostIndexQueryFilter(RowFilter filter) {
    return RowFilter.NONE; // we don't have further filtering to do
  }

  @Override
  public long getEstimatedResultRows() {
    /*
     * From http://www.doanduyhai.com/blog/?p=2058#sasi_read_path As a result, every search with SASI
     * currently always hit the same node, which is the node responsible for the first token range on
     * the cluster. Subsequent rounds of query (if any) will spread out to other nodes eventually
     */

    // Trying to be smart here, if each node returns random negative we'll get load spread and will
    // still be selected over native index
    return -Math.abs(random.nextLong());
  }

  @Override
  public Indexer indexerFor(DecoratedKey key, RegularAndStaticColumns columns, int nowInSec, WriteContext ctx,
      IndexTransaction.Type txType) {
    if (isDummyMode) {
      return NoOpIndexer.INSTANCE; // Dummy mode
    }

    if (skipLogReplay && !StorageService.instance.isInitialized()) {
      if (LOGGER.isTraceEnabled()) {
        String id = ByteBufferUtil.bytesToHex(key.getKey());
        LOGGER.trace("Index {} skipping index {} storage service is not initialized yet (commit log replay)", name, id);
      }
      return NoOpIndexer.INSTANCE;
    }

    Token token = key.getToken();
    if (skipNonLocalUpdates && !CStarUtils.isOwner(baseCfs, token)) {
      if (LOGGER.isTraceEnabled()) {
        String id = ByteBufferUtil.bytesToHex(key.getKey());
        LOGGER.trace("Index {} skipping update on {} because {} is not in our range", name, id, token);
      }
      return NoOpIndexer.INSTANCE;
    }

    return new EsIndexer(this, key, nowInSec, !analyticMode);
  }

  @Override
  public BiFunction<PartitionIterator, ReadCommand, PartitionIterator> postProcessorFor(ReadCommand command) {
    return NoOpPartitionIterator.INSTANCE;
  }

  @Override
  public Searcher searcherFor(final ReadCommand command) {
    return executionController -> search(command);
  }

  @Override
  public void validate(PartitionUpdate update) throws InvalidRequestException {
    // nothing to check for now
  }

  @Override
  public void validate(ReadCommand command) throws InvalidRequestException {
    String queryString = CStarUtils.queryString(command);
    // don't validate commands
    if (!queryString.startsWith(UPDATE) && !queryString.startsWith(GET_MAPPING) && !queryString.startsWith(PUT_MAPPING)) {
      LOGGER.trace("Index {} validate query: {}", name, queryString); // reducing level because we'll see it as search later
      esIndex.validate(queryString);
    }
  }

  public UnfilteredPartitionIterator search(ReadCommand command) {
    final Stopwatch time = Stopwatch.createStarted();
    final String searchId = UUID.randomUUID().toString();
    final String queryString = CStarUtils.queryString(command);

    if (queryString.startsWith(UPDATE)) {
      handleUpdateCommand(queryString.substring(UPDATE.length(), queryString.length() - 1));
      return EmptyIterators.unfilteredPartition(command.metadata());
    }

    if (!(command instanceof PartitionRangeReadCommand)) {
      LOGGER.error("Index {} class type {} is not supported for searches", name, command.getClass().getName());
      throw new UnsupportedOperationException(command.getClass().getName() + " is not supported for searches");
    }

    PartitionRangeReadCommand readCommand = (PartitionRangeReadCommand) command;

    Optional<Expression> expression =
        readCommand.rowFilter().getExpressions().stream().filter(exp -> exp.column().equals(this.indexColDef)).findFirst();
    if (!expression.isPresent()) {
      throw new UnsupportedOperationException(this.getClass().getName() + " not support search by non-indexed field");
    }
    int index = readCommand.rowFilter().getExpressions().indexOf(expression.get());
    readCommand.rowFilter().getExpressions().set(index, new EsSimpleExpression(expression.get()));

    if (queryString.startsWith(GET_MAPPING)) {
      return getMapping(readCommand, searchId);
    }

    if (queryString.startsWith(PUT_MAPPING)) {
      return putMapping(readCommand, queryString);
    }

    LOGGER.debug("Index {} search query {} '{}'", name, searchId, queryString);
    Tracing.trace("ESI {} Searching '{}'", searchId, queryString); // This is CQL "tracing on" support

    // Extract query metadata if any
    QueryMetaData queryMetaData = new QueryMetaData(queryString);
    SearchResult searchResult = esIndex.search(queryMetaData);

    LOGGER.debug("{} {} Found {} matching ES docs in {}ms", name, searchId, searchResult.items.size(), time.elapsed(TimeUnit.MILLISECONDS));
    Tracing.trace("ESI {} Found {} matching ES docs in {}ms", searchId, searchResult.items.size(), time.elapsed(TimeUnit.MILLISECONDS));

    if (searchResult.items.isEmpty()) {
      return EmptyIterators.unfilteredPartition(command.metadata());
    }

    fillPartitionAndClusteringKeys(searchResult.items);

    Token start = readCommand.dataRange().keyRange().left.getToken();
    Token stop = readCommand.dataRange().keyRange().right.getToken();

    if (!start.equals(stop)) { // Do we have token ranges to filter out ?
      LOGGER.info("Range queries will result in multiple ES queries, add 'and token(pk)=rnd.long' to your query");

      /*
       * We must only load a row if its DecoratedKey is within the range of requested tokens. Note that
       * the same node can receive several requests for the same search but with different ranges. If
       * filtering is not done it will return duplicates. Drawback is that ES query is sent several times
       * for the same search. Also it means ordering might not work as expected.
       */
      searchResult.items
          .removeIf(result -> !readCommand.dataRange().keyRange().contains(baseCfs.getPartitioner().decorateKey(result.partitionKey)));
    }

    if (queryMetaData.loadRows()) {
      return new StreamingPartitionIterator(this, searchResult, readCommand, searchId);
    } else {
      return new FakePartitionIterator(this, searchResult, readCommand, searchId);
    }
  }

  private String indexName() {
    return baseCfs.keyspace.getName() + "_" + baseCfs.name.toLowerCase() + INDEX_POSTFIX;
  }

  private UnfilteredPartitionIterator getMapping(PartitionRangeReadCommand readCommand, String searchId) {
    LOGGER.info("Get index mapping for {}", baseCfs);

    JsonObject mapping = esIndex.getMapping(indexName()).metadata.getAsJsonObject();
    SearchResultRow fakeRow = new SearchResultRow(new String[] {FAKE_ID}, mapping);
    fakeRow.partitionKey = ByteBufferUtil.bytes(FAKE_ID);
    SearchResult searchResult = new SearchResult(Collections.singletonList(fakeRow), mapping);
    return new FakePartitionIterator(this, searchResult, readCommand, searchId);
  }

  private UnfilteredPartitionIterator putMapping(PartitionRangeReadCommand readCommand, String queryString) {
    LOGGER.info("Put index mapping {}", queryString);

    String additionalMapping = queryString.substring(PUT_MAPPING.length(), queryString.length() - 1);
    esIndex.putMapping(indexName(), additionalMapping);
    return EmptyIterators.unfilteredPartition(readCommand.metadata());
  }

  private void handleUpdateCommand(String newSettings) {
    try {
      updateSettings(newSettings); // Update in Cassandra index options
      boolean changed = reloadSettings(); // Apply new settings and merge them with es-index.properties
      if (changed) {
        esIndex.settingsUpdated(); // Notify the ES index to create new segments
      }
    } catch (IOException e) {
      LOGGER.error("Index {} update setting error: {}", name, e.getMessage(), e);
    }
  }

  private void fillPartitionAndClusteringKeys(List<SearchResultRow> searchResultRows) {
    for (SearchResultRow searchResultRow : searchResultRows) {
      String[] rawKey = searchResultRow.primaryKey;
      final String[] partitionKeys;
      final String[] clusteringKeys;

      // separate partition and clustering keys
      if (hasClusteringColumns) {
        clusteringKeys = new String[clusteringColumnsNames.size()];
        partitionKeys = new String[partitionKeysNames.size()];

        int pkPos = 0;
        int ckPos = 0;
        for (String key : rawKey) {
          if (pkPos < partitionKeysNames.size()) {
            partitionKeys[pkPos] = key;
          } else {
            clusteringKeys[ckPos] = key;
            ckPos++;
          }
          pkPos++;
        }
      } else {
        partitionKeys = rawKey;
        clusteringKeys = null;
      }

      searchResultRow.partitionKey = CStarUtils.getPartitionKeys(partitionKeys, baseCfs.metadata());
      searchResultRow.clusteringKeys = clusteringKeys;
    }
  }

  private void updateSettings(String settings) throws IOException {
    LOGGER.info("Update {} settings to '{}'", name, settings);
    Tracing.trace("Update {} settings to '{}'", name, settings); // This is CQL "tracing on" support
    Map<String, String> options = JsonUtils.jsonStringToStringMap(settings);

    // check options
    if (!options.containsKey(IndexTarget.CUSTOM_INDEX_OPTION_NAME) || !options.containsKey(IndexTarget.TARGET_OPTION_NAME)) {
      LOGGER.warn("We do not allow to change options class_name and target");
    }
    options.put(IndexTarget.CUSTOM_INDEX_OPTION_NAME, indexMetadata.options.get(IndexTarget.CUSTOM_INDEX_OPTION_NAME));
    options.put(IndexTarget.TARGET_OPTION_NAME, indexMetadata.options.get(IndexTarget.TARGET_OPTION_NAME));

    IndexMetadata newIndexMetadata = IndexMetadata.fromSchemaMetadata(indexMetadata.name, indexMetadata.kind, options);

    TableMetadata newMetaData = baseCfs.metadata().unbuild().build();
    newMetaData.indexes.replace(newIndexMetadata);

    indexMetadata = newIndexMetadata; // assume update will work, helps for UTs
    MigrationManager.announceTableUpdate(newMetaData, false);
  }

  public ColumnMetadata getIndexColDef() {
    return indexColDef;
  }

  public ConsistencyLevel getReadConsistency() {
    return readConsistencyLevel;
  }

  /**
   * After cassandra 3.11.10 supportsReplicaFilteringProtection is added to data resolving.
   * Data resolving is executed in cassandra when consistency is greater than 1 for cassandra 3.
   * and for cassandra 4 data resolving is executed always if coordinator node is not the query executor node.
   *
   * Because of this for cassandra 3 wcc-es-index is NOT supported above 3.11.10
   * Upgrade is supported from c* 3.x to 4.x
   *
   * For details in cassandra 3 source code check ReadCallback.get() method and blockfor field.
   * For details in cassandra 4 source code SingleRangeResponse.waitForResponse(): resolver.resolve()
   * @param rowFilter
   * @return
   */
  @Override
  public boolean supportsReplicaFilteringProtection(RowFilter rowFilter) {
    return false;
  }
}
