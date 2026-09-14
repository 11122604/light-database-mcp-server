package org.turnright.mysqlmcpserver.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.turnright.mysqlmcpserver.registry.ElasticsearchDataSourceRegistry;

import java.util.*;

/**
 * Elasticsearch 数据库服务
 * 支持多数据源切换
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ElasticsearchDataSourceRegistry.class)
public class ElasticsearchService {

    private final ElasticsearchDataSourceRegistry registry;

    /**
     * 列出索引（使用默认数据源）
     */
    public List<String> listIndices() {
        return listIndices(null);
    }

    /**
     * 列出索引（指定数据源）
     */
    public List<String> listIndices(String dataSourceName) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Listing indices on datasource '{}'", actualDsName);

        List<String> indices = new ArrayList<>();

        try {
            GetIndexResponse response = client.indices().get(
                GetIndexRequest.of(r -> r.index("*"))
            );

            response.result().keySet().forEach(indices::add);
        } catch (Exception e) {
            log.error("Error listing indices: {}", e.getMessage());
            throw new RuntimeException("Failed to list indices", e);
        }

        log.info("Found {} indices", indices.size());
        return indices;
    }

    /**
     * 搜索（使用默认数据源）
     */
    public Map<String, Object> search(String index, Query query, int size, int from) {
        return search(null, index, query, size, from);
    }

    /**
     * 搜索（指定数据源）
     */
    public Map<String, Object> search(String dataSourceName, String index, Query query, int size, int from) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Searching index '{}' on datasource '{}' with size: {}, from: {}", index, actualDsName, size, from);

        try {
            SearchResponse<Map> response = client.search(s -> s
                .index(index)
                .query(query)
                .size(size)
                .from(from),
                Map.class
            );

            List<Map<String, Object>> documents = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("_id", hit.id());
                doc.put("_score", hit.score());
                doc.put("_source", hit.source());
                documents.add(doc);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("datasource", actualDsName);
            result.put("total", response.hits().total() != null ? response.hits().total().value() : 0);
            result.put("documents", documents);
            result.put("took_ms", response.took());
            return result;
        } catch (Exception e) {
            log.error("Error searching: {}", e.getMessage());
            throw new RuntimeException("Failed to search", e);
        }
    }

    /**
     * 获取文档（使用默认数据源）
     */
    public Map<String, Object> getDocument(String index, String id) {
        return getDocument(null, index, id);
    }

    /**
     * 获取文档（指定数据源）
     */
    public Map<String, Object> getDocument(String dataSourceName, String index, String id) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting document {} from index {} on datasource '{}'", id, index, actualDsName);

        try {
            GetResponse<Map> response = client.get(g -> g
                .index(index)
                .id(id),
                Map.class
            );

            if (!response.found()) {
                return Map.of("found", false);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("datasource", actualDsName);
            result.put("found", true);
            result.put("_id", response.id());
            result.put("_source", response.source());
            return result;
        } catch (Exception e) {
            log.error("Error getting document: {}", e.getMessage());
            throw new RuntimeException("Failed to get document", e);
        }
    }

    /**
     * 索引文档（使用默认数据源）
     */
    public String indexDocument(String index, String id, Map<String, Object> document) {
        return indexDocument(null, index, id, document);
    }

    /**
     * 索引文档（指定数据源）
     */
    public String indexDocument(String dataSourceName, String index, String id, Map<String, Object> document) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Indexing document {} in {} on datasource '{}'", id != null ? id : "auto-generated", index, actualDsName);

        try {
            IndexRequest<Map> request;
            if (id != null && !id.isEmpty()) {
                request = IndexRequest.of(i -> i
                    .index(index)
                    .id(id)
                    .document(document)
                );
            } else {
                request = IndexRequest.of(i -> i
                    .index(index)
                    .document(document)
                );
            }

            IndexResponse response = client.index(request);
            log.info("Document indexed with id: {}", response.id());

            return response.id();
        } catch (Exception e) {
            log.error("Error indexing document: {}", e.getMessage());
            throw new RuntimeException("Failed to index document", e);
        }
    }

    /**
     * 批量索引（使用默认数据源）
     */
    public long bulkIndex(String index, List<Map<String, Object>> documents) {
        return bulkIndex(null, index, documents);
    }

    /**
     * 批量索引（指定数据源）
     */
    public long bulkIndex(String dataSourceName, String index, List<Map<String, Object>> documents) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Bulk indexing {} documents in {} on datasource '{}'", documents.size(), index, actualDsName);

        try {
            BulkRequest.Builder br = new BulkRequest.Builder();

            for (Map<String, Object> doc : documents) {
                br.operations(op -> op
                    .index(idx -> idx
                        .index(index)
                        .document(doc)
                    )
                );
            }

            BulkResponse response = client.bulk(br.build());

            if (response.errors()) {
                log.warn("Bulk indexing had errors");
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        log.error("Error for item {}: {}", item.index(), item.error().reason());
                    }
                }
            }

            log.info("Bulk indexed {} documents", response.items().size());
            return response.items().size();
        } catch (Exception e) {
            log.error("Error bulk indexing: {}", e.getMessage());
            throw new RuntimeException("Failed to bulk index", e);
        }
    }

    /**
     * 更新文档（使用默认数据源）
     */
    public boolean updateDocument(String index, String id, Map<String, Object> document) {
        return updateDocument(null, index, id, document);
    }

    /**
     * 更新文档（指定数据源）
     */
    public boolean updateDocument(String dataSourceName, String index, String id, Map<String, Object> document) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Updating document {} in {} on datasource '{}'", id, index, actualDsName);

        try {
            UpdateResponse<Map> response = client.update(u -> u
                .index(index)
                .id(id)
                .doc(document),
                Map.class
            );

            log.info("Document updated: {}", response.result());
            return response.result() != null;
        } catch (Exception e) {
            log.error("Error updating document: {}", e.getMessage());
            throw new RuntimeException("Failed to update document", e);
        }
    }

    /**
     * 删除文档（使用默认数据源）
     */
    public boolean deleteDocument(String index, String id) {
        return deleteDocument(null, index, id);
    }

    /**
     * 删除文档（指定数据源）
     */
    public boolean deleteDocument(String dataSourceName, String index, String id) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Deleting document {} from {} on datasource '{}'", id, index, actualDsName);

        try {
            DeleteResponse response = client.delete(d -> d
                .index(index)
                .id(id)
            );

            log.info("Document deleted: {}", response.result());
            return response.result() != null;
        } catch (Exception e) {
            log.error("Error deleting document: {}", e.getMessage());
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    /**
     * 按查询删除（使用默认数据源）
     */
    public long deleteByQuery(String index, Query query) {
        return deleteByQuery(null, index, query);
    }

    /**
     * 按查询删除（指定数据源）
     */
    public long deleteByQuery(String dataSourceName, String index, Query query) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Deleting documents by query from {} on datasource '{}'", index, actualDsName);

        try {
            DeleteByQueryResponse response = client.deleteByQuery(d -> d
                .index(index)
                .query(query)
            );

            log.info("Deleted {} documents", response.deleted());
            return response.deleted();
        } catch (Exception e) {
            log.error("Error deleting by query: {}", e.getMessage());
            throw new RuntimeException("Failed to delete by query", e);
        }
    }

    /**
     * 获取索引信息（使用默认数据源）
     */
    public Map<String, Object> getIndexInfo(String index) {
        return getIndexInfo(null, index);
    }

    /**
     * 获取索引信息（指定数据源）
     */
    public Map<String, Object> getIndexInfo(String dataSourceName, String index) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting info for index '{}' on datasource '{}'", index, actualDsName);

        try {
            GetIndexResponse response = client.indices().get(
                GetIndexRequest.of(r -> r.index(index))
            );

            var indexInfo = response.result().get(index);
            if (indexInfo == null) {
                return Map.of("exists", false);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("datasource", actualDsName);
            result.put("exists", true);
            result.put("index", index);
            result.put("aliases", indexInfo.aliases() != null ? indexInfo.aliases().keySet() : Set.of());
            result.put("mappings", indexInfo.mappings() != null ? indexInfo.mappings() : Map.of());
            result.put("settings", indexInfo.settings() != null ? indexInfo.settings() : Map.of());
            return result;
        } catch (Exception e) {
            log.error("Error getting index info: {}", e.getMessage());
            throw new RuntimeException("Failed to get index info", e);
        }
    }

    /**
     * 创建索引（使用默认数据源）
     */
    public boolean createIndex(String index, Map<String, Object> mappings, Map<String, Object> settings) {
        return createIndex(null, index, mappings, settings);
    }

    /**
     * 创建索引（指定数据源）
     */
    public boolean createIndex(String dataSourceName, String index, Map<String, Object> mappings, Map<String, Object> settings) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Creating index '{}' on datasource '{}'", index, actualDsName);

        try {
            // Simple index creation - complex mappings/settings can be added later
            client.indices().create(c -> c.index(index));
            log.info("Index created: {}", index);
            return true;
        } catch (Exception e) {
            log.error("Error creating index: {}", e.getMessage());
            throw new RuntimeException("Failed to create index", e);
        }
    }

    /**
     * 删除索引（使用默认数据源）
     */
    public boolean deleteIndex(String index) {
        return deleteIndex(null, index);
    }

    /**
     * 删除索引（指定数据源）
     */
    public boolean deleteIndex(String dataSourceName, String index) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Deleting index '{}' on datasource '{}'", index, actualDsName);

        try {
            client.indices().delete(
                DeleteIndexRequest.of(d -> d.index(index))
            );
            log.info("Index deleted: {}", index);
            return true;
        } catch (Exception e) {
            log.error("Error deleting index: {}", e.getMessage());
            throw new RuntimeException("Failed to delete index", e);
        }
    }

    /**
     * 统计文档（使用默认数据源）
     */
    public Map<String, Object> countDocuments(String index, Query query) {
        return countDocuments(null, index, query);
    }

    /**
     * 统计文档（指定数据源）
     */
    public Map<String, Object> countDocuments(String dataSourceName, String index, Query query) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Counting documents in index '{}' on datasource '{}'", index, actualDsName);

        try {
            CountResponse response;
            if (query != null) {
                response = client.count(c -> c
                    .index(index)
                    .query(query)
                );
            } else {
                response = client.count(c -> c.index(index));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("datasource", actualDsName);
            result.put("count", response.count());
            return result;
        } catch (Exception e) {
            log.error("Error counting documents: {}", e.getMessage());
            throw new RuntimeException("Failed to count documents", e);
        }
    }

    /**
     * 获取集群信息（使用默认数据源）
     */
    public Map<String, Object> getClusterInfo() {
        return getClusterInfo(null);
    }

    /**
     * 获取集群信息（指定数据源）
     */
    public Map<String, Object> getClusterInfo(String dataSourceName) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting cluster info on datasource '{}'", actualDsName);

        try {
            var infoResponse = client.info();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("datasource", actualDsName);
            result.put("cluster_name", infoResponse.clusterName());
            result.put("cluster_uuid", infoResponse.clusterUuid());
            result.put("version", infoResponse.version() != null ? infoResponse.version().number() : "unknown");
            return result;
        } catch (Exception e) {
            log.error("Error getting cluster info: {}", e.getMessage());
            throw new RuntimeException("Failed to get cluster info", e);
        }
    }

    /**
     * 获取集群健康状态（使用默认数据源）
     */
    public Map<String, Object> getClusterHealth() {
        return getClusterHealth(null);
    }

    /**
     * 获取集群健康状态（指定数据源）
     */
    public Map<String, Object> getClusterHealth(String dataSourceName) {
        ElasticsearchClient client = registry.getDataSource(dataSourceName);
        String actualDsName = dataSourceName != null ? dataSourceName : registry.getDefaultDataSourceName();
        log.info("Getting cluster health on datasource '{}'", actualDsName);

        try {
            var health = client.cluster().health();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("datasource", actualDsName);
            result.put("status", health.status() != null ? health.status().jsonValue() : "unknown");
            result.put("number_of_nodes", health.numberOfNodes());
            result.put("number_of_data_nodes", health.numberOfDataNodes());
            result.put("active_shards", health.activeShards());
            result.put("relocating_shards", health.relocatingShards());
            result.put("unassigned_shards", health.unassignedShards());
            return result;
        } catch (Exception e) {
            log.error("Error getting cluster health: {}", e.getMessage());
            throw new RuntimeException("Failed to get cluster health", e);
        }
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
}