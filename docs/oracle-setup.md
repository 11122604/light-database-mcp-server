# Oracle JDBC 驱动安装指南

由于 Oracle JDBC 驱动的许可证限制，不能自动从 Maven Central 下载。需要手动安装。

> ⚠️ 仓库中 `lib/ojdbc11.jar` 目前只是一个**占位文件**（约 948 字节，不含任何驱动类）。启用 Oracle 前必须用官方 `ojdbc11.jar`（约 5MB）替换它，或按下方"方法二"安装到本地 Maven 仓库。否则运行时会因找不到 `oracle.jdbc.OracleDriver` 而连接失败。

## 方法一：从 Oracle 网站下载

1. 访问 Oracle JDBC 下载页面：
   https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html

2. 下载适合您 Java 版本的驱动：
   - Java 11+: ojdbc11.jar
   - Java 8: ojdbc8.jar

3. 将下载的 jar 文件放入项目的 `lib` 目录：
   ```
   database-mcp-server/lib/ojdbc11.jar
   ```

## 方法二：手动安装到 Maven 本地仓库

```bash
mvn install:install-file \
  -Dfile=ojdbc11.jar \
  -DgroupId=com.oracle.database.jdbc \
  -DartifactId=ojdbc11 \
  -Dversion=23.3.0.0 \
  -Dpackaging=jar
```

然后修改 pom.xml 使用 `provided` scope：
```xml
<dependency>
    <groupId>com.oracle.database.jdbc</groupId>
    <artifactId>ojdbc11</artifactId>
    <version>23.3.0.0</version>
    <scope>provided</scope>
</dependency>
```

## 方法三：使用 Oracle Maven 仓库

在 pom.xml 添加 Oracle 仓库：

```xml
<repositories>
    <repository>
        <id>oracle-repo</id>
        <url>https://repo.oracle.com/maven</url>
    </repository>
</repositories>
```

## 连接字符串格式

Oracle JDBC 支持多种连接字符串格式：

### SID 格式（传统）
```
jdbc:oracle:thin:@host:port:SID
jdbc:oracle:thin:@localhost:1521:ORCL
```

### Service Name 格式（推荐）
```
jdbc:oracle:thin:@//host:port/service_name
jdbc:oracle:thin:@//localhost:1521/orclpdb1
```

### TNS 别名格式
```
jdbc:oracle:thin:@TNS_ALIAS_NAME
```

### 完整配置示例
```
jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(HOST=localhost)(PORT=1521)(PROTOCOL=tcp))(CONNECT_DATA=(SERVICE_NAME=orclpdb1)))
```

## 配置示例

```bash
# 仅多数据源格式（单数据源写法已移除；单源也需 datasources，name 建议 default）
ORACLE_ENABLED=true
ORACLE_DATASOURCES_0_NAME=primary
ORACLE_DATASOURCES_0_URL=jdbc:oracle:thin:@//prod-server:1521/prod_db
ORACLE_DATASOURCES_0_USERNAME=app_user
ORACLE_DATASOURCES_0_PASSWORD=secure_pass
ORACLE_DATASOURCES_1_NAME=reporting
ORACLE_DATASOURCES_1_URL=jdbc:oracle:thin:@//report-server:1521/report_db
ORACLE_DATASOURCES_1_USERNAME=report_user
ORACLE_DATASOURCES_1_PASSWORD=secure_pass
ORACLE_DEFAULT_DATASOURCE=primary
```

## 常见问题

### Q: 编译时报找不到 Oracle 驱动错误？
A: 确保 `lib/ojdbc11.jar` 文件存在，或已安装到 Maven 本地仓库。

### Q: 运行时报 ClassNotFoundException？
A: 确保运行时环境中包含 Oracle JDBC 驱动。可以：
- 将驱动放入应用服务器 lib 目录
- 使用 Docker 时在镜像中添加驱动
- 或打包时包含驱动（需要修改 pom.xml scope）

### Q: 连接失败 ORA-12514？
A: 检查 Service Name 是否正确。SID 和 Service Name 是不同的概念。

### Q: SSL 连接问题？
A: 添加 SSL 参数：
```
jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(HOST=host)(PORT=port)(PROTOCOL=tcp))(CONNECT_DATA=(SERVICE_NAME=name))(SECURITY=(SSL_SERVER_CERT_DN="CN=...")))
```