package org.turnright.mysqlmcpserver.admin;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.turnright.mysqlmcpserver.config.*;
import org.turnright.mysqlmcpserver.registry.*;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理界面核心：展示当前数据源配置、保存（写回 mcp-config.env）、热增删改数据源、实时连接测试。
 *
 * 生效方式：
 *  - 已启用类型：新增/修改/删除立即热注册/热删（registry 增删连接池/客户端）
 *  - 未启用类型（enabled=false）：仅保存到 mcp-config.env，需设置启用并重启生效
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "mcp.transport", havingValue = "http")
public class AdminConfigManager {

    private final DatabaseProperties mysqlProps;
    private final SqlServerProperties sqlserverProps;
    private final OracleProperties oracleProps;
    private final MongoProperties mongoProps;
    private final ElasticsearchProperties esProps;
    private final RedisProperties redisProps;

    @Autowired(required = false)
    private Map<String, JdbcDataSourceRegistry> jdbcRegistries;
    @Autowired(required = false)
    private MongoDataSourceRegistry mongoRegistry;
    @Autowired(required = false)
    private ElasticsearchDataSourceRegistry esRegistry;
    @Autowired(required = false)
    private RedisDataSourceRegistry redisRegistry;

    @Value("${mcp.admin-token:}")
    private String adminToken;

    /** 管理界面动态创建的 ES transport，由本类在关闭/替换/删除时负责释放 */
    private final Map<String, co.elastic.clients.transport.ElasticsearchTransport> managedEsTransports =
        new ConcurrentHashMap<>();

    public AdminConfigManager(DatabaseProperties mysqlProps, SqlServerProperties sqlserverProps,
                              OracleProperties oracleProps, MongoProperties mongoProps,
                              ElasticsearchProperties esProps, RedisProperties redisProps) {
        this.mysqlProps = mysqlProps;
        this.sqlserverProps = sqlserverProps;
        this.oracleProps = oracleProps;
        this.mongoProps = mongoProps;
        this.esProps = esProps;
        this.redisProps = redisProps;
    }

    public boolean isWritable() {
        return adminToken != null && !adminToken.isEmpty();
    }

    public boolean checkToken(String token) {
        return isWritable() && adminToken.equals(token);
    }

    // ==================== 状态 ====================

    public Map<String, Object> buildState() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("writable", isWritable());
        out.put("note", isWritable() ? "" : "未配置 MCP_ADMIN_TOKEN，配置修改(保存)被禁用，仅可查看与测试");
        List<Map<String, Object>> types = new ArrayList<>();
        types.add(stateFor("mysql", mysqlProps.isEnabled(), mysqlProps.getDefaultName(), "MySQL", "jdbc"));
        types.add(stateFor("sqlserver", sqlserverProps.isEnabled(), sqlserverProps.getDefaultName(), "SQL Server", "jdbc"));
        types.add(stateFor("oracle", oracleProps.isEnabled(), oracleProps.getDefaultName(), "Oracle", "jdbc"));
        types.add(stateFor("mongodb", mongoProps.isEnabled(), mongoProps.getDefaultName(), "MongoDB", "mongodb"));
        types.add(stateFor("elasticsearch", esProps.isEnabled(), esProps.getDefaultName(), "Elasticsearch", "elasticsearch"));
        types.add(stateFor("redis", redisProps.isEnabled(), redisProps.getDefaultName(), "Redis", "redis"));
        out.put("types", types);
        return out;
    }

    private Map<String, Object> stateFor(String type, boolean enabled, String defaultName,
                                         String label, String kind) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("label", label);
        m.put("kind", kind);
        m.put("enabled", enabled);
        m.put("defaultName", defaultName);
        List<Map<String, Object>> list = new ArrayList<>();
        switch (type) {
            case "mysql":
            case "sqlserver":
            case "oracle":
                for (DataSourceConfig c : jdbcDatasources(type)) {
                    if (c == null) continue;
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("name", c.getName());
                    f.put("url", c.getUrl());
                    f.put("username", c.getUsername());
                    f.put("password", "");
                    f.put("hasPassword", c.getPassword() != null && !c.getPassword().isEmpty());
                    f.put("description", c.getDescription());
                    f.put("readOnly", c.isReadOnly());
                    list.add(f);
                }
                break;
            case "mongodb":
                for (MongoDataSourceConfig c : mongoProps.getAllDatasourceConfigs()) {
                    if (c == null) continue;
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("name", c.getName());
                    f.put("uri", c.getUri());
                    f.put("database", c.getDatabase());
                    f.put("host", c.getHost());
                    f.put("port", c.getPort());
                    f.put("username", c.getUsername());
                    f.put("password", "");
                    f.put("hasPassword", c.getPassword() != null && !c.getPassword().isEmpty());
                    f.put("description", c.getDescription());
                    f.put("readOnly", c.isReadOnly());
                    list.add(f);
                }
                break;
            case "elasticsearch":
                for (ElasticsearchDataSourceConfig c : esProps.getAllDatasourceConfigs()) {
                    if (c == null) continue;
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("name", c.getName());
                    f.put("host", c.getHost());
                    f.put("port", c.getPort());
                    f.put("username", c.getUsername());
                    f.put("password", "");
                    f.put("hasPassword", c.getPassword() != null && !c.getPassword().isEmpty());
                    // apiKey 属于密钥，不回显明文；仅标记是否存在，编辑留空=保留
                    f.put("apiKey", "");
                    f.put("hasApiKey", c.getApiKey() != null && !c.getApiKey().isEmpty());
                    f.put("ssl", c.isSsl());
                    f.put("description", c.getDescription());
                    f.put("readOnly", c.isReadOnly());
                    list.add(f);
                }
                break;
            case "redis":
                for (RedisDataSourceConfig c : redisProps.getAllDatasourceConfigs()) {
                    if (c == null) continue;
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("name", c.getName());
                    f.put("host", c.getHost());
                    f.put("port", c.getPort());
                    f.put("password", "");
                    f.put("hasPassword", c.getPassword() != null && !c.getPassword().isEmpty());
                    f.put("description", c.getDescription());
                    f.put("readOnly", c.isReadOnly());
                    list.add(f);
                }
                break;
            default:
                break;
        }
        m.put("datasources", list);
        return m;
    }

    // ==================== 保存 / 热操作 ====================

    /**
     * 新增或修改一个数据源：写回 mcp-config.env + 热注册/热删。
     * @return result map {ok, message, created, hotApplied}
     */
    public Map<String, Object> upsert(String type, Map<String, Object> fields) {
        String name = str(fields.get("name"));
        if (name == null || name.trim().isEmpty()) {
            return err("name 不能为空");
        }
        boolean enabled;
        boolean created;
        boolean hot = false;
        String hotNote = "";
        switch (type) {
            case "mysql":
            case "sqlserver":
            case "oracle": {
                List<DataSourceConfig> list = jdbcDatasources(type);
                int idx = indexOfName(list, name);
                created = idx < 0;
                DataSourceConfig cfg = buildJdbcConfig(type, fields, idx >= 0 ? list.get(idx) : null);
                if (created) list.add(cfg); else list.set(idx, cfg);
                enabled = jdbcEnabled(type);
                if (enabled) {
                    JdbcDataSourceRegistry reg = jdbcRegistry(type);
                    if (reg != null) {
                        reg.remove(name); // 幂等；热删旧连接
                        reg.register(name, createHikari(type, cfg), cfg.getDescription(), cfg.isReadOnly());
                        hot = true;
                    }
                }
                break;
            }
            case "mongodb": {
                List<MongoDataSourceConfig> list = mongoProps.getAllDatasourceConfigs();
                int idx = indexOfName(list, name);
                created = idx < 0;
                MongoDataSourceConfig cfg = buildMongoConfig(fields, idx >= 0 ? list.get(idx) : null);
                if (created) list.add(cfg); else list.set(idx, cfg);
                enabled = mongoProps.isEnabled();
                if (enabled && mongoRegistry != null) {
                    mongoRegistry.remove(name);
                    mongoRegistry.register(name, createMongoClient(cfg), cfg.getDescription(), cfg.isReadOnly(), cfg.getDatabase());
                    hot = true;
                }
                break;
            }
            case "elasticsearch": {
                List<ElasticsearchDataSourceConfig> list = esProps.getAllDatasourceConfigs();
                int idx = indexOfName(list, name);
                created = idx < 0;
                ElasticsearchDataSourceConfig cfg = buildEsConfig(fields, idx >= 0 ? list.get(idx) : null);
                if (created) list.add(cfg); else list.set(idx, cfg);
                enabled = esProps.isEnabled();
                if (enabled && esRegistry != null) {
                    closeManagedEs(name);
                    esRegistry.remove(name);
                    co.elastic.clients.transport.ElasticsearchTransport transport = createEsClient(cfg);
                    esRegistry.register(name, new co.elastic.clients.elasticsearch.ElasticsearchClient(transport),
                        cfg.getDescription(), cfg.isReadOnly());
                    managedEsTransports.put(name, transport);
                    hot = true;
                }
                break;
            }
            case "redis": {
                List<RedisDataSourceConfig> list = redisProps.getAllDatasourceConfigs();
                int idx = indexOfName(list, name);
                created = idx < 0;
                RedisDataSourceConfig cfg = buildRedisConfig(fields, idx >= 0 ? list.get(idx) : null);
                if (created) list.add(cfg); else list.set(idx, cfg);
                enabled = redisProps.isEnabled();
                if (enabled && redisRegistry != null) {
                    redisRegistry.remove(name);
                    redisRegistry.register(name, createRedisPool(cfg), cfg.getDescription(), cfg.isReadOnly());
                    hot = true;
                }
                break;
            }
            default:
                return err("未知类型: " + type);
        }

        // 设为默认
        if (fields.containsKey("default")) {
            setDefaultFor(type, bool(fields.get("default"), false) ? name : null);
        }

        try {
            writeEnvFile();
        } catch (IOException e) {
            log.error("Failed to write mcp-config.env: {}", e.getMessage(), e);
            return err("配置已生效但写入 mcp-config.env 失败: " + e.getMessage());
        }

        if (!enabled) {
            hotNote = "该类型当前未启用，改动已保存；启用后重启才生效";
        } else if (!hot) {
            hotNote = "配置已保存；当前进程缺少该类型注册表，重启后生效";
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("created", created);
        r.put("message", created ? "数据源已新增" : "数据源已更新");
        r.put("hotApplied", hot);
        r.put("note", hotNote);
        return r;
    }

    public Map<String, Object> delete(String type, String name) {
        if (name == null || name.trim().isEmpty()) {
            return err("name 不能为空");
        }
        switch (type) {
            case "mysql":
            case "sqlserver":
            case "oracle": {
                List<DataSourceConfig> list = jdbcDatasources(type);
                if (!list.removeIf(c -> c != null && name.equals(c.getName()))) {
                    return err("数据源不存在: " + name);
                }
                JdbcDataSourceRegistry reg = jdbcRegistry(type);
                if (reg != null) reg.remove(name);
                break;
            }
            case "mongodb": {
                List<MongoDataSourceConfig> list = mongoProps.getAllDatasourceConfigs();
                if (!list.removeIf(c -> c != null && name.equals(c.getName()))) {
                    return err("数据源不存在: " + name);
                }
                if (mongoRegistry != null) mongoRegistry.remove(name);
                break;
            }
            case "elasticsearch": {
                List<ElasticsearchDataSourceConfig> list = esProps.getAllDatasourceConfigs();
                if (!list.removeIf(c -> c != null && name.equals(c.getName()))) {
                    return err("数据源不存在: " + name);
                }
                if (esRegistry != null) esRegistry.remove(name);
                closeManagedEs(name);
                break;
            }
            case "redis": {
                List<RedisDataSourceConfig> list = redisProps.getAllDatasourceConfigs();
                if (!list.removeIf(c -> c != null && name.equals(c.getName()))) {
                    return err("数据源不存在: " + name);
                }
                if (redisRegistry != null) redisRegistry.remove(name);
                break;
            }
            default:
                return err("未知类型: " + type);
        }
        // 若删除的是默认源，清除默认名
        if (name.equals(getDefaultFor(type))) {
            setDefaultFor(type, null);
        }
        try {
            writeEnvFile();
        } catch (IOException e) {
            log.error("Failed to write mcp-config.env: {}", e.getMessage(), e);
            return err("删除已生效但写入 mcp-config.env 失败: " + e.getMessage());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("message", "数据源已删除");
        return r;
    }

    /**
     * 连接测试（用给定字段做一次性连接，不注册）。
     * 前端不回传密码明文：当 password 为空且给定 name 指向已存在数据源时，取存储的密码用于测试。
     */
    private void fillStoredPassword(String type, Map<String, Object> fields) {
        if (!str(fields.get("password")).isEmpty()) {
            return;
        }
        String nm = strOrNull(fields.get("name"));
        if (nm == null) {
            return;
        }
        String stored = null;
        switch (type) {
            case "mysql":
            case "sqlserver":
            case "oracle":
                for (DataSourceConfig c : jdbcDatasources(type)) {
                    if (c != null && nm.equals(c.getName())) { stored = c.getPassword(); break; }
                }
                break;
            case "mongodb":
                for (MongoDataSourceConfig c : mongoProps.getAllDatasourceConfigs()) {
                    if (c != null && nm.equals(c.getName())) { stored = c.getPassword(); break; }
                }
                break;
            case "elasticsearch":
                for (ElasticsearchDataSourceConfig c : esProps.getAllDatasourceConfigs()) {
                    if (c != null && nm.equals(c.getName())) {
                        stored = c.getPassword();
                        // ES 也支持 apiKey 认证，检测时若页面未给则用存储的
                        if (str(fields.get("apiKey")).isEmpty() && c.getApiKey() != null && !c.getApiKey().isEmpty()) {
                            fields.put("apiKey", c.getApiKey());
                        }
                        break;
                    }
                }
                break;
            case "redis":
                for (RedisDataSourceConfig c : redisProps.getAllDatasourceConfigs()) {
                    if (c != null && nm.equals(c.getName())) { stored = c.getPassword(); break; }
                }
                break;
            default:
                break;
        }
        if (stored != null) {
            fields.put("password", stored);
        }
    }
    public Map<String, Object> test(String type, Map<String, Object> fields) {
        fillStoredPassword(type, fields);
        Map<String, Object> r = new LinkedHashMap<>();
        long start = System.nanoTime();
        try {
            switch (type) {
                case "mysql":
                case "sqlserver":
                case "oracle":
                    testJdbc(type, fields);
                    break;
                case "mongodb":
                    testMongo(fields);
                    break;
                case "elasticsearch":
                    testEs(fields);
                    break;
                case "redis":
                    testRedis(fields);
                    break;
                default:
                    return err("未知类型: " + type);
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            r.put("ok", true);
            r.put("message", "连接成功 (" + ms + " ms)");
            r.put("latencyMs", ms);
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            String msg = String.valueOf(e.getMessage());
            if (msg == null || msg.length() > 200) {
                msg = e.getClass().getSimpleName() + (msg != null ? ": " + msg.substring(0, Math.min(200, msg.length())) : "");
            }
            r.put("ok", false);
            r.put("message", "连接失败: " + msg + " (" + ms + " ms)");
            r.put("latencyMs", ms);
        }
        return r;
    }

    // ==================== 配置对象构造 ====================

    private DataSourceConfig buildJdbcConfig(String type, Map<String, Object> f, DataSourceConfig existing) {
        DataSourceConfig c = new DataSourceConfig();
        c.setName(str(f.get("name")));
        c.setUrl(str(f.get("url")));
        c.setUsername(strOrNull(f.get("username")));
        String pwd = str(f.get("password"));
        if (pwd == null || pwd.isEmpty()) {
            c.setPassword(existing != null ? existing.getPassword() : null);
        } else {
            c.setPassword(pwd);
        }
        c.setDescription(strOrNull(f.get("description")));
        c.setReadOnly(bool(f.get("readOnly"), true));
        if (existing != null) {
            c.setMaxPoolSize(existing.getMaxPoolSize());
            c.setMinIdle(existing.getMinIdle());
            c.setIdleTimeout(existing.getIdleTimeout());
            c.setConnectionTimeout(existing.getConnectionTimeout());
        }
        return c;
    }

    private MongoDataSourceConfig buildMongoConfig(Map<String, Object> f, MongoDataSourceConfig existing) {
        MongoDataSourceConfig c = new MongoDataSourceConfig();
        c.setName(str(f.get("name")));
        c.setUri(strOrNull(f.get("uri")));
        c.setDatabase(strOrNull(f.get("database")));
        c.setHost(strOrNull(f.get("host")));
        c.setPort(intOr(f.get("port"), 27017));
        c.setUsername(strOrNull(f.get("username")));
        String pwd = str(f.get("password"));
        c.setPassword((pwd == null || pwd.isEmpty()) && existing != null ? existing.getPassword() : pwd);
        c.setDescription(strOrNull(f.get("description")));
        c.setReadOnly(bool(f.get("readOnly"), true));
        return c;
    }

    private ElasticsearchDataSourceConfig buildEsConfig(Map<String, Object> f, ElasticsearchDataSourceConfig existing) {
        ElasticsearchDataSourceConfig c = new ElasticsearchDataSourceConfig();
        c.setName(str(f.get("name")));
        c.setHost(str(f.get("host")));
        c.setPort(intOr(f.get("port"), 9200));
        c.setUsername(strOrNull(f.get("username")));
        String pwd = str(f.get("password"));
        c.setPassword((pwd == null || pwd.isEmpty()) && existing != null ? existing.getPassword() : pwd);
        String ak = str(f.get("apiKey"));
        c.setApiKey((ak == null || ak.isEmpty()) && existing != null ? existing.getApiKey() : strOrNull(ak));
        c.setSsl(bool(f.get("ssl"), false));
        c.setDescription(strOrNull(f.get("description")));
        c.setReadOnly(bool(f.get("readOnly"), true));
        return c;
    }

    private RedisDataSourceConfig buildRedisConfig(Map<String, Object> f, RedisDataSourceConfig existing) {
        RedisDataSourceConfig c = new RedisDataSourceConfig();
        c.setName(str(f.get("name")));
        c.setHost(str(f.get("host")));
        c.setPort(intOr(f.get("port"), 6379));
        String pwd = str(f.get("password"));
        c.setPassword((pwd == null || pwd.isEmpty()) && existing != null ? existing.getPassword() : pwd);
        c.setDescription(strOrNull(f.get("description")));
        c.setReadOnly(bool(f.get("readOnly"), true));
        return c;
    }

    // ==================== 运行时连接构造 ====================

    private HikariDataSource createHikari(String type, DataSourceConfig c) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(c.getUrl());
        hc.setUsername(c.getUsername());
        hc.setPassword(c.getPassword());
        switch (type) {
            case "mysql": hc.setDriverClassName("com.mysql.cj.jdbc.Driver"); break;
            case "sqlserver": hc.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver"); break;
            case "oracle": hc.setDriverClassName("oracle.jdbc.OracleDriver"); break;
            default: throw new IllegalArgumentException("未知 JDBC 类型: " + type);
        }
        hc.setMaximumPoolSize(c.getMaxPoolSize());
        hc.setMinimumIdle(c.getMinIdle());
        hc.setIdleTimeout(c.getIdleTimeout());
        hc.setConnectionTimeout(c.getConnectionTimeout());
        hc.setPoolName("admin-" + type + "-" + c.getName());
        hc.setInitializationFailTimeout(-1); // 懒初始化
        return new HikariDataSource(hc);
    }

    private MongoClient createMongoClient(MongoDataSourceConfig c) {
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(buildMongoConnString(c)))
            .build();
        return MongoClients.create(settings);
    }

    /**
     * 构造 MongoDB 连接串。URI 与独立用户名/密码同时提供时，把凭据注入 URI，
     * 避免出现"填了密码却被 URI 忽略、无认证也能连上"的假成功。
     */
    private String buildMongoConnString(MongoDataSourceConfig c) {
        boolean hasCreds = c.getUsername() != null && !c.getUsername().isEmpty()
            && c.getPassword() != null && !c.getPassword().isEmpty();
        String uri = c.getUri();
        if (uri != null && !uri.isEmpty()) {
            if (!hasCreds) {
                return uri;
            }
            String user = encMongoUserinfo(c.getUsername());
            String pwd = encMongoUserinfo(c.getPassword());
            int schemeEnd = uri.indexOf("://");
            String rest = uri.substring(schemeEnd + 3);
            int at = rest.indexOf('@');
            int slash = rest.indexOf('/');
            // 仅当 @ 位于主机部分（slash 前）才算已有凭据，可安全剥离
            if (at >= 0 && (slash < 0 || at < slash)) {
                rest = rest.substring(at + 1);
            }
            return uri.substring(0, schemeEnd + 3) + user + ":" + pwd + "@" + rest;
        }
        // 无 URI：host[:port] 形式
        StringBuilder sb = new StringBuilder("mongodb://");
        if (hasCreds) {
            sb.append(encMongoUserinfo(c.getUsername())).append(":")
              .append(encMongoUserinfo(c.getPassword())).append("@");
        }
        sb.append(c.getHost() != null ? c.getHost() : "localhost").append(":").append(c.getPort());
        if (c.getDatabase() != null && !c.getDatabase().isEmpty()) {
            sb.append("/").append(c.getDatabase());
        }
        return sb.toString();
    }

    /** MongoDB 连接串 userinfo 需 percent-encode（@ : / 等字符） */
    private static String encMongoUserinfo(String v) {
        try {
            return java.net.URLEncoder.encode(v == null ? "" : v, "UTF-8");
        } catch (Exception e) {
            return v;
        }
    }

    private co.elastic.clients.transport.ElasticsearchTransport createEsClient(ElasticsearchDataSourceConfig c) {
        org.apache.http.HttpHost host = new org.apache.http.HttpHost(
            c.getHost(), c.getPort(), c.isSsl() ? "https" : "http");
        org.elasticsearch.client.RestClientBuilder builder = org.elasticsearch.client.RestClient.builder(host);
        if (c.getUsername() != null && c.getPassword() != null
            && !c.getUsername().isEmpty() && !c.getPassword().isEmpty()) {
            org.apache.http.impl.client.BasicCredentialsProvider creds =
                new org.apache.http.impl.client.BasicCredentialsProvider();
            creds.setCredentials(org.apache.http.auth.AuthScope.ANY,
                new org.apache.http.auth.UsernamePasswordCredentials(c.getUsername(), c.getPassword()));
            builder.setHttpClientConfigCallback(b -> b.setDefaultCredentialsProvider(creds));
        }
        org.elasticsearch.client.RestClient rc = builder.build();
        return new co.elastic.clients.transport.rest_client.RestClientTransport(rc,
            new co.elastic.clients.json.jackson.JacksonJsonpMapper());
    }

    private JedisPool createRedisPool(RedisDataSourceConfig c) {
        JedisPoolConfig pc = new JedisPoolConfig();
        pc.setMaxTotal(c.getMaxTotal());
        pc.setMaxIdle(c.getMaxIdle());
        pc.setMinIdle(c.getMinIdle());
        if (c.getPassword() != null && !c.getPassword().isEmpty()) {
            return new JedisPool(pc, c.getHost(), c.getPort(), c.getTimeout(), c.getPassword());
        }
        return new JedisPool(pc, c.getHost(), c.getPort(), c.getTimeout());
    }

    // ==================== 连接测试实现 ====================

    private void testJdbc(String type, Map<String, Object> f) throws Exception {
        String url = str(f.get("url"));
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("url 不能为空");
        }
        String user = str(f.get("username"));
        String pwd = str(f.get("password"));
        DriverManager.setLoginTimeout(8);
        try (Connection conn = DriverManager.getConnection(url, user, pwd);
             Statement st = conn.createStatement()) {
            String sql = "oracle".equals(type) ? "SELECT 1 FROM DUAL" : "SELECT 1";
            try (java.sql.ResultSet rs = st.executeQuery(sql)) {
                if (!rs.next()) {
                    throw new IllegalStateException("查询未返回结果");
                }
            }
        }
    }

    private void testMongo(Map<String, Object> f) throws Exception {
        MongoDataSourceConfig c = buildMongoConfig(f, null);
        if ((c.getUri() == null || c.getUri().isEmpty()) && c.getHost() == null) {
            throw new IllegalArgumentException("uri 或 host 至少填一个");
        }
        try (MongoClient client = createMongoClient(c)) {
            client.getDatabase("admin").runCommand(new org.bson.Document("ping", 1));
        }
    }

    private void testEs(Map<String, Object> f) throws Exception {
        ElasticsearchDataSourceConfig c = buildEsConfig(f, null);
        if (c.getHost() == null || c.getHost().isEmpty()) {
            throw new IllegalArgumentException("host 不能为空");
        }
        co.elastic.clients.transport.ElasticsearchTransport t = createEsClient(c);
        try (t) {
            co.elastic.clients.elasticsearch.ElasticsearchClient client =
                new co.elastic.clients.elasticsearch.ElasticsearchClient(t);
            client.ping();
        }
    }

    private void testRedis(Map<String, Object> f) throws Exception {
        RedisDataSourceConfig c = buildRedisConfig(f, null);
        if (c.getHost() == null || c.getHost().isEmpty()) {
            throw new IllegalArgumentException("host 不能为空");
        }
        try (redis.clients.jedis.Jedis jedis = new redis.clients.jedis.Jedis(c.getHost(), c.getPort(), c.getTimeout())) {
            if (c.getPassword() != null && !c.getPassword().isEmpty()) {
                jedis.auth(c.getPassword());
            }
            String pong = jedis.ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("ping 未返回 PONG: " + pong);
            }
        }
    }

    // ==================== mcp-config.env 读写 ====================

    private File envFile() {
        return new File("mcp-config.env");
    }

    private void writeEnvFile() throws IOException {
        String transport = "stdio";
        String port = "8080";
        String adminTokenLine = ""; // 保留文件里的管理口令（避免保存时抹掉）
        File f = envFile();
        if (f.exists()) {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.startsWith("MCP_TRANSPORT=")) transport = t.substring("MCP_TRANSPORT=".length()).trim();
                else if (t.startsWith("MCP_PORT=")) port = t.substring("MCP_PORT=".length()).trim();
                else if (t.startsWith("MCP_ADMIN_TOKEN=")) {
                    String v = t.substring("MCP_ADMIN_TOKEN=".length()).trim();
                    if (!v.isEmpty()) adminTokenLine = "MCP_ADMIN_TOKEN=" + v;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Turnright Database MCP Server 配置（由管理界面保存生成，多数据源格式）\n");
        sb.append("MCP_TRANSPORT=").append(transport).append("\n");
        sb.append("MCP_PORT=").append(port).append("\n");
        if (!adminTokenLine.isEmpty()) {
            sb.append(adminTokenLine).append("\n");
        }
        sb.append("\n");
        sb.append(jdbcSection("MYSQL_ENABLED", "MYSQL_DEFAULT_DATASOURCE", "DATABASE_DATASOURCES_",
            mysqlProps.isEnabled(), mysqlProps.getDefaultName(), mysqlProps.getAllDatasourceConfigs(), JdbcRowMapper));
        sb.append("\n");
        sb.append(jdbcSection("SQLSERVER_ENABLED", "SQLSERVER_DEFAULT_DATASOURCE", "SQLSERVER_DATASOURCES_",
            sqlserverProps.isEnabled(), sqlserverProps.getDefaultName(), sqlserverProps.getAllDatasourceConfigs(), JdbcRowMapper));
        sb.append("\n");
        sb.append(jdbcSection("ORACLE_ENABLED", "ORACLE_DEFAULT_DATASOURCE", "ORACLE_DATASOURCES_",
            oracleProps.isEnabled(), oracleProps.getDefaultName(), oracleProps.getAllDatasourceConfigs(), JdbcRowMapper));
        sb.append("\n");
        sb.append(mongoSection());
        sb.append("\n");
        sb.append(esSection());
        sb.append("\n");
        sb.append(redisSection());
        Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        log.info("mcp-config.env rewritten ({} bytes)", f.length());
    }

    private static final java.util.function.BiFunction<DataSourceConfig, String, String[][]> JdbcRowMapper =
        (c, pre) -> new String[][]{
            {pre + "NAME", c.getName()},
            {pre + "URL", nz(c.getUrl())},
            {pre + "USERNAME", nz(c.getUsername())},
            {pre + "PASSWORD", nz(c.getPassword())},
            {pre + "DESCRIPTION", nz(c.getDescription())},
            {pre + "READONLY", String.valueOf(c.isReadOnly())},
        };

    private String jdbcSection(String enableKey, String defaultKey, String pre, boolean enabled,
                               String defaultName, List<DataSourceConfig> list,
                               java.util.function.BiFunction<DataSourceConfig, String, String[][]> mapper) {
        StringBuilder s = new StringBuilder();
        s.append(enableKey).append("=").append(enabled).append("\n");
        if (defaultName != null && !defaultName.isEmpty()) {
            s.append(defaultKey).append("=").append(defaultName).append("\n");
        }
        int i = 0;
        for (DataSourceConfig c : list) {
            if (c == null) continue;
            for (String[] kv : mapper.apply(c, pre + i + "_")) {
                if (kv[1] != null && !kv[1].isEmpty()) {
                    s.append(kv[0]).append("=").append(kv[1]).append("\n");
                }
            }
            i++;
        }
        return s.toString();
    }

    private String mongoSection() {
        StringBuilder s = new StringBuilder();
        s.append("MONGODB_ENABLED=").append(mongoProps.isEnabled()).append("\n");
        String dn = mongoProps.getDefaultName();
        if (dn != null && !dn.isEmpty()) s.append("MONGODB_DEFAULT_DATASOURCE=").append(dn).append("\n");
        int i = 0;
        for (MongoDataSourceConfig c : mongoProps.getAllDatasourceConfigs()) {
            if (c == null) continue;
            String pre = "MONGODB_DATASOURCES_" + i + "_";
            put(s, pre + "NAME", c.getName());
            put(s, pre + "URI", c.getUri());
            put(s, pre + "DATABASE", c.getDatabase());
            put(s, pre + "HOST", c.getHost());
            put(s, pre + "PORT", String.valueOf(c.getPort()));
            put(s, pre + "USERNAME", c.getUsername());
            put(s, pre + "PASSWORD", c.getPassword());
            put(s, pre + "DESCRIPTION", c.getDescription());
            put(s, pre + "READONLY", String.valueOf(c.isReadOnly()));
            i++;
        }
        return s.toString();
    }

    private String esSection() {
        StringBuilder s = new StringBuilder();
        s.append("ES_ENABLED=").append(esProps.isEnabled()).append("\n");
        String dn = esProps.getDefaultName();
        if (dn != null && !dn.isEmpty()) s.append("ES_DEFAULT_DATASOURCE=").append(dn).append("\n");
        int i = 0;
        for (ElasticsearchDataSourceConfig c : esProps.getAllDatasourceConfigs()) {
            if (c == null) continue;
            String pre = "ES_DATASOURCES_" + i + "_";
            put(s, pre + "NAME", c.getName());
            put(s, pre + "HOST", c.getHost());
            put(s, pre + "PORT", String.valueOf(c.getPort()));
            put(s, pre + "USERNAME", c.getUsername());
            put(s, pre + "PASSWORD", c.getPassword());
            put(s, pre + "API_KEY", c.getApiKey());
            put(s, pre + "SSL", String.valueOf(c.isSsl()));
            put(s, pre + "DESCRIPTION", c.getDescription());
            put(s, pre + "READONLY", String.valueOf(c.isReadOnly()));
            i++;
        }
        return s.toString();
    }

    private String redisSection() {
        StringBuilder s = new StringBuilder();
        s.append("REDIS_ENABLED=").append(redisProps.isEnabled()).append("\n");
        String dn = redisProps.getDefaultName();
        if (dn != null && !dn.isEmpty()) s.append("REDIS_DEFAULT_DATASOURCE=").append(dn).append("\n");
        int i = 0;
        for (RedisDataSourceConfig c : redisProps.getAllDatasourceConfigs()) {
            if (c == null) continue;
            String pre = "REDIS_DATASOURCES_" + i + "_";
            put(s, pre + "NAME", c.getName());
            put(s, pre + "HOST", c.getHost());
            put(s, pre + "PORT", String.valueOf(c.getPort()));
            put(s, pre + "PASSWORD", c.getPassword());
            put(s, pre + "DESCRIPTION", c.getDescription());
            put(s, pre + "READONLY", String.valueOf(c.isReadOnly()));
            i++;
        }
        return s.toString();
    }

    private static void put(StringBuilder s, String key, String val) {
        if (val != null && !val.isEmpty()) {
            s.append(key).append("=").append(val).append("\n");
        }
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    // ==================== 辅助 ====================

    private List<DataSourceConfig> jdbcDatasources(String type) {
        switch (type) {
            case "sqlserver": return sqlserverProps.getAllDatasourceConfigs();
            case "oracle": return oracleProps.getAllDatasourceConfigs();
            default: return mysqlProps.getAllDatasourceConfigs();
        }
    }

    private boolean jdbcEnabled(String type) {
        switch (type) {
            case "sqlserver": return sqlserverProps.isEnabled();
            case "oracle": return oracleProps.isEnabled();
            default: return mysqlProps.isEnabled();
        }
    }

    private JdbcDataSourceRegistry jdbcRegistry(String type) {
        if (jdbcRegistries == null) return null;
        switch (type) {
            case "sqlserver": return jdbcRegistries.get("sqlServerDataSourceRegistry");
            case "oracle": return jdbcRegistries.get("oracleDataSourceRegistry");
            default: return jdbcRegistries.get("mysqlDataSourceRegistry");
        }
    }

    private String getDefaultFor(String type) {
        switch (type) {
            case "mysql": return mysqlProps.getDefaultName();
            case "sqlserver": return sqlserverProps.getDefaultName();
            case "oracle": return oracleProps.getDefaultName();
            case "mongodb": return mongoProps.getDefaultName();
            case "elasticsearch": return esProps.getDefaultName();
            case "redis": return redisProps.getDefaultName();
            default: return null;
        }
    }

    private void setDefaultFor(String type, String name) {
        switch (type) {
            case "mysql": mysqlProps.setDefaultName(name); break;
            case "sqlserver": sqlserverProps.setDefaultName(name); break;
            case "oracle": oracleProps.setDefaultName(name); break;
            case "mongodb": mongoProps.setDefaultName(name); break;
            case "elasticsearch": esProps.setDefaultName(name); break;
            case "redis": redisProps.setDefaultName(name); break;
            default: break;
        }
    }

    private void closeManagedEs(String name) {
        co.elastic.clients.transport.ElasticsearchTransport t = managedEsTransports.remove(name);
        if (t != null) {
            try { t.close(); } catch (Exception e) { log.warn("close es transport {}: {}", name, e.getMessage()); }
        }
    }

    @PreDestroy
    public void shutdown() {
        for (co.elastic.clients.transport.ElasticsearchTransport t : managedEsTransports.values()) {
            try { t.close(); } catch (Exception ignored) { }
        }
        managedEsTransports.clear();
    }

    private static Map<String, Object> err(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", false);
        r.put("message", msg);
        return r;
    }

    private static <T> int indexOfName(List<T> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            T c = list.get(i);
            if (c != null && name.equals(configName(c))) {
                return i;
            }
        }
        return -1;
    }

    private static String configName(Object o) {
        if (o instanceof DataSourceConfig) return ((DataSourceConfig) o).getName();
        if (o instanceof MongoDataSourceConfig) return ((MongoDataSourceConfig) o).getName();
        if (o instanceof ElasticsearchDataSourceConfig) return ((ElasticsearchDataSourceConfig) o).getName();
        if (o instanceof RedisDataSourceConfig) return ((RedisDataSourceConfig) o).getName();
        return null;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String strOrNull(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isEmpty() ? null : s;
    }

    private static boolean bool(Object v, boolean def) {
        if (v == null) return def;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static int intOr(Object v, int def) {
        if (v == null) return def;
        try {
            return (int) Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }
}
