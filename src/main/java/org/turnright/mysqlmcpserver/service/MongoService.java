package org.turnright.mysqlmcpserver.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.turnright.mysqlmcpserver.registry.MongoDataSourceRegistry;

import java.util.*;

/**
 * MongoDB 数据库服务
 * 支持多数据源切换
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(MongoDataSourceRegistry.class)
public class MongoService {

    private final MongoDataSourceRegistry registry;

    /**
     * 单次 find 最大返回行数（防御超大 limit 全量物化导致 OOM）
     */
    private static final int MAX_FIND_LIMIT = 10000;

    /**
     * 聚合结果最大返回行数（防御大集合聚合全量物化导致 OOM）
     */
    private static final int MAX_AGGREGATE_ROWS = 10000;

    /**
     * 列出数据库（使用默认数据源）
     */
    public List<String> listDatabases() {
        return listDatabases(null);
    }

    /**
     * 列出数据库（指定数据源）
     */
    public List<String> listDatabases(String dataSourceName) {
        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Listing databases on datasource '{}'", actualDsName);

        List<String> databases = new ArrayList<>();

        MongoIterable<String> dbNames = client.listDatabaseNames();
        for (String name : dbNames) {
            databases.add(name);
        }

        log.info("Found {} databases", databases.size());
        return databases;
    }

    /**
     * 列出集合（使用默认数据源）
     */
    public List<String> listCollections(String databaseName) {
        return listCollections(null, databaseName);
    }

    /**
     * 列出集合（指定数据源）
     */
    public List<String> listCollections(String dataSourceName, String databaseName) {
        databaseName = resolveDatabaseName(dataSourceName, databaseName);

        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Listing collections in database '{}' on datasource '{}'", databaseName, actualDsName);

        List<String> collections = new ArrayList<>();

        MongoDatabase database = client.getDatabase(databaseName);
        MongoIterable<String> collNames = database.listCollectionNames();
        for (String name : collNames) {
            collections.add(name);
        }

        log.info("Found {} collections", collections.size());
        return collections;
    }

    /**
     * 查找文档（使用默认数据源）
     */
    public List<Map<String, Object>> findDocuments(String databaseName, String collectionName,
                                                    Document filter, Document projection,
                                                    Document sort, int limit) {
        return findDocuments(null, databaseName, collectionName, filter, projection, sort, limit);
    }

    /**
     * 查找文档（指定数据源）
     */
    public List<Map<String, Object>> findDocuments(String dataSourceName, String databaseName, String collectionName,
                                                    Document filter, Document projection,
                                                    Document sort, int limit) {
        databaseName = resolveDatabaseName(dataSourceName, databaseName);
        if (collectionName == null || collectionName.isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Finding documents in {}.{} on datasource '{}'", databaseName, collectionName, actualDsName);

        MongoDatabase database = client.getDatabase(databaseName);
        MongoCollection<Document> collection = database.getCollection(collectionName);

        // Build query with proper type handling
        com.mongodb.client.FindIterable<Document> findIterable;
        if (filter != null && !filter.isEmpty()) {
            findIterable = collection.find(filter);
        } else {
            findIterable = collection.find();
        }

        // Apply projection
        if (projection != null && !projection.isEmpty()) {
            findIterable = findIterable.projection(projection);
        }

        // Apply sort
        if (sort != null && !sort.isEmpty()) {
            findIterable = findIterable.sort(sort);
        }

        // Apply limit
        if (limit > 0) {
            if (limit > MAX_FIND_LIMIT) {
                log.warn("Find limit {} exceeds max {}, clamping to {}", limit, MAX_FIND_LIMIT, MAX_FIND_LIMIT);
                limit = MAX_FIND_LIMIT;
            }
            findIterable = findIterable.limit(limit);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Document doc : findIterable) {
            results.add(convertDocumentToMap(doc));
        }

        log.info("Found {} documents", results.size());
        return results;
    }

    /**
     * 统计文档（使用默认数据源）
     */
    public long countDocuments(String databaseName, String collectionName, Document filter) {
        return countDocuments(null, databaseName, collectionName, filter);
    }

    /**
     * 统计文档（指定数据源）
     */
    public long countDocuments(String dataSourceName, String databaseName, String collectionName, Document filter) {
        databaseName = resolveDatabaseName(dataSourceName, databaseName);
        if (collectionName == null || collectionName.isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Counting documents in {}.{} on datasource '{}'", databaseName, collectionName, actualDsName);

        MongoDatabase database = client.getDatabase(databaseName);
        MongoCollection<Document> collection = database.getCollection(collectionName);

        long count;
        if (filter != null && !filter.isEmpty()) {
            count = collection.countDocuments(filter);
        } else {
            count = collection.countDocuments();
        }

        log.info("Count: {}", count);
        return count;
    }

    /**
     * 插入文档（使用默认数据源）
     */
    public long insertDocument(String databaseName, String collectionName, Document document) {
        return insertDocument(null, databaseName, collectionName, document);
    }

    /**
     * 插入文档（指定数据源）
     */
    public long insertDocument(String dataSourceName, String databaseName, String collectionName, Document document) {
        databaseName = resolveDatabaseName(dataSourceName, databaseName);
        if (collectionName == null || collectionName.isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (document == null || document.isEmpty()) {
            throw new IllegalArgumentException("Document cannot be null or empty");
        }

        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Inserting document into {}.{} on datasource '{}'", databaseName, collectionName, actualDsName);

        MongoDatabase database = client.getDatabase(databaseName);
        MongoCollection<Document> collection = database.getCollection(collectionName);

        collection.insertOne(document);
        log.info("Document inserted successfully");

        return 1;
    }

    /**
     * 批量插入文档（使用默认数据源）
     */
    public long insertDocuments(String databaseName, String collectionName, List<Document> documents) {
        return insertDocuments(null, databaseName, collectionName, documents);
    }

    /**
     * 批量插入文档（指定数据源）
     */
    public long insertDocuments(String dataSourceName, String databaseName, String collectionName, List<Document> documents) {
        databaseName = resolveDatabaseName(dataSourceName, databaseName);
        if (collectionName == null || collectionName.isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("Documents list cannot be null or empty");
        }

        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Inserting {} documents into {}.{} on datasource '{}'", documents.size(), databaseName, collectionName, actualDsName);

        MongoDatabase database = client.getDatabase(databaseName);
        MongoCollection<Document> collection = database.getCollection(collectionName);

        collection.insertMany(documents);
        log.info("Documents inserted successfully");

        return documents.size();
    }

    /**
     * 更新文档（使用默认数据源）
     */
    public long updateDocuments(String databaseName, String collectionName,
                                 Document filter, Document update, boolean multi) {
        return updateDocuments(null, databaseName, collectionName, filter, update, multi);
    }

    /**
     * 更新文档（指定数据源）
     */
    public long updateDocuments(String dataSourceName, String databaseName, String collectionName,
                                 Document filter, Document update, boolean multi) {
        databaseName = resolveDatabaseName(dataSourceName, databaseName);
        if (collectionName == null || collectionName.isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (update == null || update.isEmpty()) {
            throw new IllegalArgumentException("Update document cannot be null or empty");
        }

        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Updating documents in {}.{} on datasource '{}'", databaseName, collectionName, actualDsName);

        MongoDatabase database = client.getDatabase(databaseName);
        MongoCollection<Document> collection = database.getCollection(collectionName);

        // 若 update 顶层已含 $ 操作符（$inc/$unset/$push...），直接使用；否则按替换语义包 $set
        Document updateDoc;
        boolean hasUpdateOperator = false;
        for (String key : update.keySet()) {
            if (key.startsWith("$")) {
                hasUpdateOperator = true;
                break;
            }
        }
        updateDoc = hasUpdateOperator ? update : new Document("$set", update);

        Document actualFilter = filter != null ? filter : new Document();
        // 空 filter 兜底拦截：防止直接调用 service 时整集合/首条被意外更新
        if (actualFilter.isEmpty()) {
            throw new IllegalArgumentException("Update requires a non-empty filter to prevent unintended mass updates");
        }
        if (multi) {
            long modified = collection.updateMany(actualFilter, updateDoc).getModifiedCount();
            log.info("Modified {} documents", modified);
            return modified;
        } else {
            long modified = collection.updateOne(actualFilter, updateDoc).getModifiedCount();
            log.info("Modified {} document", modified);
            return modified;
        }
    }

    /**
     * 删除文档（使用默认数据源）
     */
    public long deleteDocuments(String databaseName, String collectionName, Document filter, boolean multi) {
        return deleteDocuments(null, databaseName, collectionName, filter, multi);
    }

    /**
     * 删除文档（指定数据源）
     */
    public long deleteDocuments(String dataSourceName, String databaseName, String collectionName, Document filter, boolean multi) {
        databaseName = resolveDatabaseName(dataSourceName, databaseName);
        if (collectionName == null || collectionName.isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Deleting documents from {}.{} on datasource '{}'", databaseName, collectionName, actualDsName);

        MongoDatabase database = client.getDatabase(databaseName);
        MongoCollection<Document> collection = database.getCollection(collectionName);

        // 空 filter 兜底拦截：deleteMany/deleteOne 空条件会误删任意文档
        Document actualFilter = filter != null ? filter : new Document();
        if (actualFilter.isEmpty()) {
            throw new IllegalArgumentException("Delete requires a non-empty filter to prevent unintended data loss");
        }

        if (multi) {
            long deleted = collection.deleteMany(actualFilter).getDeletedCount();
            log.info("Deleted {} documents", deleted);
            return deleted;
        } else {
            long deleted = collection.deleteOne(actualFilter).getDeletedCount();
            log.info("Deleted {} document", deleted);
            return deleted;
        }
    }

    /**
     * 获取集合统计（使用默认数据源）
     */
    public Map<String, Object> getCollectionStats(String databaseName, String collectionName) {
        return getCollectionStats(null, databaseName, collectionName);
    }

    /**
     * 获取集合统计（指定数据源）
     */
    public Map<String, Object> getCollectionStats(String dataSourceName, String databaseName, String collectionName) {
        databaseName = resolveDatabaseName(dataSourceName, databaseName);
        if (collectionName == null || collectionName.isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting stats for {}.{} on datasource '{}'", databaseName, collectionName, actualDsName);

        MongoDatabase database = client.getDatabase(databaseName);

        Document statsCommand = new Document("collStats", collectionName);
        Document stats = database.runCommand(statsCommand);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasource", actualDsName);
        result.put("ns", stats.get("ns"));
        result.put("count", stats.get("count"));
        result.put("size", stats.get("size"));
        result.put("storageSize", stats.get("storageSize"));
        result.put("totalIndexSize", stats.get("totalIndexSize"));
        result.put("indexDetails", stats.get("indexDetails"));

        return result;
    }

    /**
     * 聚合查询（使用默认数据源）
     */
    public List<Map<String, Object>> aggregate(String databaseName, String collectionName,
                                                List<? extends Bson> pipeline) {
        return aggregate(null, databaseName, collectionName, pipeline);
    }

    /**
     * 聚合查询（指定数据源）
     */
    public List<Map<String, Object>> aggregate(String dataSourceName, String databaseName, String collectionName,
                                                List<? extends Bson> pipeline) {
        databaseName = resolveDatabaseName(dataSourceName, databaseName);
        if (collectionName == null || collectionName.isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }
        if (pipeline == null || pipeline.isEmpty()) {
            throw new IllegalArgumentException("Pipeline cannot be null or empty");
        }

        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Running aggregation on {}.{} on datasource '{}'", databaseName, collectionName, actualDsName);

        MongoDatabase database = client.getDatabase(databaseName);
        MongoCollection<Document> collection = database.getCollection(collectionName);

        List<Map<String, Object>> results = new ArrayList<>();
        // 追加 $limit 兜底：即使调用方未限制 pipeline，也不会全量物化导致 OOM
        List<Bson> cappedPipeline = new ArrayList<>(pipeline);
        cappedPipeline.add(new Document("$limit", MAX_AGGREGATE_ROWS));
        for (Document doc : collection.aggregate(cappedPipeline)) {
            results.add(convertDocumentToMap(doc));
        }

        log.info("Aggregation returned {} documents", results.size());
        return results;
    }

    /**
     * 获取索引名称（使用默认数据源）
     */
    public List<String> getIndexNames(String databaseName, String collectionName) {
        return getIndexNames(null, databaseName, collectionName);
    }

    /**
     * 获取索引名称（指定数据源）
     */
    public List<String> getIndexNames(String dataSourceName, String databaseName, String collectionName) {
        databaseName = resolveDatabaseName(dataSourceName, databaseName);
        if (collectionName == null || collectionName.isEmpty()) {
            throw new IllegalArgumentException("Collection name cannot be null or empty");
        }

        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting indexes for {}.{} on datasource '{}'", databaseName, collectionName, actualDsName);

        MongoDatabase database = client.getDatabase(databaseName);
        MongoCollection<Document> collection = database.getCollection(collectionName);

        List<String> indexes = new ArrayList<>();
        for (Document index : collection.listIndexes()) {
            indexes.add(index.getString("name"));
        }

        return indexes;
    }

    /**
     * 获取数据库信息（使用默认数据源）
     */
    public Map<String, Object> getDatabaseInfo() {
        return getDatabaseInfo(null);
    }

    /**
     * 获取数据库信息（指定数据源）
     */
    public Map<String, Object> getDatabaseInfo(String dataSourceName) {
        MongoClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting MongoDB info for datasource '{}'", actualDsName);

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("datasource", actualDsName);

        // Get server info
        MongoDatabase adminDb = client.getDatabase("admin");
        Document serverStatus = adminDb.runCommand(new Document("serverStatus", 1));
        info.put("server_version", serverStatus.get("version"));
        info.put("uptime_seconds", serverStatus.get("uptime"));

        return info;
    }

    /**
     * 获取可用数据源列表
     */
    public Set<String> getAvailableDatasources() {
        return registry.getDataSourceNames();
    }

    /**
     * 获取默认数据源名称
     */
    public String getDefaultDatasourceName() {
        return registry.getDefaultDataSourceName();
    }

    /**
     * 获取数据源元数据（包含描述）
     */
    public Map<String, String> getDatasourceMetadata() {
        return registry.getDataSourceMetadata();
    }

    /**
     * 检查数据源是否为只读模式
     * @param datasource 数据源名称（null 使用默认）
     */
    public boolean isReadOnly(String datasource) {
        return registry.isReadOnly(datasource);
    }

    /**
     * 解析实际使用的库名：优先调用方显式指定的 database，其次数据源配置的默认库
     */
    private String resolveDatabaseName(String dataSourceName, String databaseName) {
        if (databaseName != null && !databaseName.isEmpty()) {
            return databaseName;
        }
        String defaultDb = registry.getDefaultDatabase(dataSourceName);
        if (defaultDb == null || defaultDb.isEmpty()) {
            String dsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
            throw new IllegalArgumentException("No database specified and datasource '" + dsName
                + "' has no default database configured. Provide 'database' parameter or configure a default database for the datasource.");
        }
        return defaultDb;
    }

    private Map<String, Object> convertDocumentToMap(Document doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String key : doc.keySet()) {
            map.put(key, convertBsonValue(doc.get(key)));
        }
        return map;
    }

    /**
     * 递归转换 BSON 值：ObjectId/Date/Binary/Decimal128 等需转为可 JSON 序列化形式，
     * 否则 _id 会被 Jackson 当 bean 输出成内部结构、Date 输出成 epoch 数字。
     */
    private Object convertBsonValue(Object value) {
        if (value instanceof Document) {
            return convertDocumentToMap((Document) value);
        }
        if (value instanceof List) {
            List<Object> convertedList = new ArrayList<>();
            for (Object item : (List<?>) value) {
                convertedList.add(convertBsonValue(item));
            }
            return convertedList;
        }
        if (value instanceof ObjectId) {
            return ((ObjectId) value).toHexString();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant().toString();
        }
        if (value instanceof Binary) {
            return java.util.Base64.getEncoder().encodeToString(((Binary) value).getData());
        }
        if (value instanceof Decimal128) {
            return value.toString();
        }
        return value;
    }
}