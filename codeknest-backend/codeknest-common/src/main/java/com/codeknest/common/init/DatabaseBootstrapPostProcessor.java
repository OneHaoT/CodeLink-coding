package com.codeknest.common.init;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 部署自举：MySQL 库不存在时自动创建（幂等 —— 已存在则跳过）。
 *
 * <p><b>为什么必须在 EnvironmentPostProcessor 阶段执行</b>：数据源地址形如
 * {@code jdbc:mysql://host:3306/codeknest}，库不存在时该连接直接失败，
 * 因此「建库」必须发生在 Spring 数据源被使用之前。本类只负责<b>建库</b>，
 * 建表与种子数据交由 Spring 原生 {@code spring.sql.init}
 * （见 {@code classpath:db/schema.sql}、{@code classpath:db/data.sql}）。
 *
 * <p><b>执行顺序</b>：本类不声明 {@code @Order}，取默认的
 * {@link org.springframework.core.Ordered#LOWEST_PRECEDENCE}，晚于
 * {@code ConfigDataEnvironmentPostProcessor}（{@code HIGHEST_PRECEDENCE + 10}），
 * 因此能读到 {@code spring.config.import} 引入的 {@code codeknest-common.yml} 中的数据源配置。
 *
 * <p><b>幂等与并发</b>：先查 {@code information_schema.SCHEMATA} 再决定是否创建；
 * 取 MySQL 命名锁 {@code codeknest:bootstrap} 防止 server-web 与 server-admin 同时启动时竞争。
 *
 * <p><b>容错</b>：任何异常仅记 WARN 并继续启动（与 ES 索引自举的风格一致）；
 * 库确实不可用时，后续数据源初始化会暴露真实错误。日志不打印任何凭据。
 *
 * <p><b>日志</b>：本类执行时日志系统尚未初始化，故必须用 Boot 注入的
 * {@link DeferredLogFactory} 取 Log —— 消息先缓存，待上下文就绪后由
 * {@code EnvironmentPostProcessorApplicationListener} 统一重放，否则日志会丢失。
 */
public class DatabaseBootstrapPostProcessor implements EnvironmentPostProcessor {

    /** 从数据源地址中解析 host / port / database，忽略后续连接参数 */
    private static final Pattern MYSQL_URL_PATTERN = Pattern.compile(
            "^jdbc:mysql://(?<host>[^:/?]+)(?::(?<port>\\d+))?/(?<database>[^;?]*)(?:[?;].*)?$");

    /** 库名白名单校验，防止配置注入 */
    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    private static final String LOCK_NAME = "codeknest:bootstrap";
    private static final int LOCK_TIMEOUT_SECONDS = 30;
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;

    private final Log log;

    public DatabaseBootstrapPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(getClass());
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 用 Binder 读取：与 Spring Boot 自身的宽松占位符解析保持一致
        // （未注入的环境变量会保留为 ${VAR} 字面量，而不是抛异常）
        Binder binder = Binder.get(environment);
        String url = binder.bind("spring.datasource.url", String.class).orElse(null);
        if (url == null || !url.startsWith("jdbc:mysql:")) {
            return;
        }

        String username = binder.bind("spring.datasource.username", String.class).orElse(null);
        String password = binder.bind("spring.datasource.password", String.class).orElse(null);
        if (!isConfigured(username) || !isConfigured(password)) {
            log.info("MySQL 自举跳过：未配置数据库凭据（DB_USER / DB_PWD）");
            return;
        }

        Matcher matcher = MYSQL_URL_PATTERN.matcher(url);
        if (!matcher.matches()) {
            log.warn("MySQL 自举跳过：无法从数据源地址解析出 host / 库名");
            return;
        }
        String host = matcher.group("host");
        String port = StringUtils.hasText(matcher.group("port")) ? matcher.group("port") : "3306";
        String database = matcher.group("database");
        if (!StringUtils.hasText(database) || !DATABASE_NAME_PATTERN.matcher(database).matches()) {
            log.warn("MySQL 自举跳过：数据源地址中的库名缺失或非法");
            return;
        }

        // 连「无库名」的服务级地址，否则库不存在时连接直接失败
        String serverUrl = "jdbc:mysql://" + host + ":" + port
                + "/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=" + CONNECT_TIMEOUT_MILLIS;

        try (Connection connection = DriverManager.getConnection(serverUrl, username, password)) {
            if (!tryLock(connection)) {
                log.warn("MySQL 自举跳过：获取初始化锁超时，可能另一实例正在初始化");
                return;
            }
            try {
                if (databaseExists(connection, database)) {
                    log.info("MySQL 库 " + database + " 已存在，跳过创建");
                    return;
                }
                log.info("MySQL 库 " + database + " 不存在，正在创建");
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE DATABASE `" + database + "` "
                            + "DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci");
                }
                log.info("MySQL 库 " + database + " 创建成功");
            } finally {
                releaseLock(connection);
            }
        } catch (Exception e) {
            log.warn("MySQL 库 " + database + " 自举失败（应用继续启动）：" + e.getMessage());
        }
    }

    /** 已配置 = 非空且占位符已被解析（未注入的环境变量会保留 ${VAR} 字面量） */
    private static boolean isConfigured(String value) {
        return StringUtils.hasText(value) && !value.contains("${");
    }

    private static boolean tryLock(Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            ps.setString(1, LOCK_NAME);
            ps.setInt(2, LOCK_TIMEOUT_SECONDS);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private void releaseLock(Connection connection) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, LOCK_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                // 仅需执行，结果无意义；连接关闭时锁也会自动释放
            }
        } catch (SQLException e) {
            log.debug("释放 MySQL 自举锁失败（连接关闭后会自动释放）：" + e.getMessage());
        }
    }

    private static boolean databaseExists(Connection connection, String database) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
            ps.setString(1, database);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
