package org.turnright.mysqlmcpserver.model;

import lombok.Getter;

@Getter
public enum DatabaseType {
    MYSQL("mysql", "MySQL"),
    SQLSERVER("sqlserver", "SQL Server"),
    MONGODB("mongodb", "MongoDB"),
    ELASTICSEARCH("elasticsearch", "Elasticsearch");

    private final String code;
    private final String displayName;

    DatabaseType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public static DatabaseType fromCode(String code) {
        for (DatabaseType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return MYSQL; // default
    }
}