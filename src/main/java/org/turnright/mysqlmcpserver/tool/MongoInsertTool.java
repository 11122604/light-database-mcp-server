package org.turnright.mysqlmcpserver.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.turnright.mysqlmcpserver.model.McpToolResult;
import org.turnright.mysqlmcpserver.service.MongoService;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(MongoService.class)
public class MongoInsertTool implements McpToolHandler {

    private final MongoService mongoService;

    @Override
    public String getName() {
        return "mongo_insert";
    }

    @Override
    public String getDescription() {
        return "Insert a document into a MongoDB collection. " +
               "Optional 'datasource' parameter to specify which configured MongoDB datasource to use.";
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
                "database", Map.of(
                    "type", "string",
                    "description", "Database name (optional - defaults to the datasource's configured database)"
                ),
                "collection", Map.of(
                    "type", "string",
                    "description", "Collection name"
                ),
                "document", Map.of(
                    "type", "object",
                    "description", "Document to insert (as JSON object)"
                )
            ),
            "required", List.of("collection", "document")
        );
    }

    @Override
    public McpToolResult execute(Map<String, Object> arguments) {
        log.info("Tool {} called", getName());

        try {
            String datasource = (String) arguments.get("datasource");
            String database = (String) arguments.get("database");
            String collection = (String) arguments.get("collection");
            Map<String, Object> documentMap = (Map<String, Object>) arguments.get("document");

            if (collection == null || documentMap == null) {
                return McpToolResult.error("Missing required parameters: collection and document");
            }

            // Safety check: readOnly mode
            if (mongoService.isReadOnly(datasource)) {
                return McpToolResult.error("SAFETY: Datasource '" +
                    (datasource != null ? datasource : mongoService.getDefaultDatasourceName()) +
                    "' is in read-only mode. Write operations are disabled. " +
                    "Set readOnly=false in configuration to enable writes.");
            }

            Document document = new Document(documentMap);
            long inserted = mongoService.insertDocument(datasource, database, collection, document);

            return McpToolResult.success("Document inserted successfully on datasource '" +
                (datasource != null ? datasource : mongoService.getDefaultDatasourceName()) +
                "'. Inserted count: " + inserted);

        } catch (Exception e) {
            log.error("Error executing MongoDB insert: {}", e.getMessage(), e);
            return McpToolResult.error("Failed to insert document: " + e.getMessage());
        }
    }
}