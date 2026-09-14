package org.turnright.mysqlmcpserver.tool;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.ElasticsearchService;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ElasticsearchService.class)
public class EsSearchTool implements McpToolHandler {

    private final ElasticsearchService esService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "es_search";
    }

    @Override
    public String getDescription() {
        return "Search documents in an Elasticsearch index. Supports full query DSL. " +
               "Optional 'datasource' parameter to specify which configured Elasticsearch datasource to use.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "datasource", Map.of(
                    "type", "string",
                    "description", "Datasource name to use (optional, uses default if not specified)"
                ),
                "index", Map.of(
                    "type", "string",
                    "description", "Index name to search"
                ),
                "query", Map.of(
                    "type", "object",
                    "description", "Elasticsearch query DSL (e.g., match, term, bool queries)"
                ),
                "size", Map.of(
                    "type", "integer",
                    "description", "Maximum number of results (default: 10)"
                ),
                "from", Map.of(
                    "type", "integer",
                    "description", "Offset for pagination (default: 0)"
                )
            ),
            "required", List.of("index", "query")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String index = (String) arguments.get("index");
            Map<String, Object> queryMap = (Map<String, Object>) arguments.get("query");
            Object sizeVal = arguments.get("size");
            Object fromVal = arguments.get("from");
            int size = sizeVal instanceof Number ? ((Number) sizeVal).intValue() : 10;
            int from = fromVal instanceof Number ? ((Number) fromVal).intValue() : 0;

            if (index == null || queryMap == null) {
                return McpToolResult.error("Missing required parameters: index and query");
            }

            Map<String, Object> results = esService.search(datasource, index, buildQuery(queryMap), size, from);

            String jsonResult = objectMapper.writeValueAsString(Map.of(
                "success", true,
                "datasource", datasource != null ? datasource : esService.getDefaultDatasourceName(),
                "index", index,
                "results", results
            ));

            return McpToolResult.success(jsonResult);

        } catch (Exception e) {
            log.error("Error executing Elasticsearch search: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to search: " + e.getMessage());
        }
    }

    private Query buildQuery(Map<String, Object> queryMap) {
        // Build a match_all query as default, or use the provided query
        if (queryMap.containsKey("match_all")) {
            return Query.of(q -> q.matchAll(m -> m));
        } else if (queryMap.containsKey("match")) {
            Map<String, Object> match = (Map<String, Object>) queryMap.get("match");
            String field = match.keySet().iterator().next();
            String value = match.get(field).toString();
            return Query.of(q -> q.match(m -> m
                .field(field)
                .query(value)
            ));
        } else if (queryMap.containsKey("term")) {
            Map<String, Object> term = (Map<String, Object>) queryMap.get("term");
            String field = term.keySet().iterator().next();
            Object value = term.get(field);
            return Query.of(q -> q.term(t -> t
                .field(field)
                .value(value.toString())
            ));
        } else if (queryMap.containsKey("range")) {
            Map<String, Object> range = (Map<String, Object>) queryMap.get("range");
            String field = range.keySet().iterator().next();
            Map<String, Object> rangeParams = (Map<String, Object>) range.get(field);
            return Query.of(q -> q.range(r -> {
                r.field(field);
                if (rangeParams.containsKey("gte")) {
                    r.gte(JsonData.of(rangeParams.get("gte")));
                }
                if (rangeParams.containsKey("lte")) {
                    r.lte(JsonData.of(rangeParams.get("lte")));
                }
                return r;
            }));
        }

        // 不静默回退 match_all：未知 DSL 结构会被当成"查全库"，是最危险的静默错误
        throw new IllegalArgumentException(
            "Unsupported query DSL structure. Only 'match_all', 'match', 'term', 'range' top-level keys are supported. "
            + "Received keys: " + queryMap.keySet());
    }
}