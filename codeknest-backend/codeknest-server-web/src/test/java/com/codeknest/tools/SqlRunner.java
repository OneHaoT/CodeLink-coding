package com.codeknest.tools;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 SQL 执行工具。
 * 从 classpath 读取数据源配置（application.yml 与 codeknest-common.yml，
 * 阶段 2 配置归一化后数据源位于后者），执行任意 SQL 并打印结果。
 * 配置中的 ${VAR:default} 占位符按环境变量解析。
 * 仅用于开发环境调试，不会打包进生产 jar（位于 test 目录）。
 *
 * 用法：
 *   java -cp <classpath> com.codeknest.tools.SqlRunner "SELECT * FROM t_user LIMIT 5"
 */
public class SqlRunner {

    /** 数据源配置来源，后读的覆盖先读的 */
    private static final String[] CONFIG_RESOURCES = {"application.yml", "codeknest-common.yml"};

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_.]+)(?::([^}]*))?}");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("用法: SqlRunner \"<SQL语句>\"");
            System.exit(1);
        }
        String sql = String.join(" ", args);

        String url = null, username = null, password = null, driver = "com.mysql.cj.jdbc.Driver";
        for (String resource : CONFIG_RESOURCES) {
            try (InputStream in = SqlRunner.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    continue;
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                boolean inDs = false;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("datasource:")) { inDs = true; continue; }
                    if (inDs) {
                        // datasource 的同级下一个顶级 key（不以空格开头且不含 datasource）
                        if (!line.startsWith("    ") && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                            break;
                        }
                        if (trimmed.startsWith("url:")) url = resolve(trimmed.substring(4).trim());
                        else if (trimmed.startsWith("username:")) username = resolve(trimmed.substring(9).trim());
                        else if (trimmed.startsWith("password:")) password = resolve(trimmed.substring(9).trim());
                        else if (trimmed.startsWith("driver-class-name:")) driver = resolve(trimmed.substring(18).trim());
                    }
                }
            }
        }
        if (url == null || username == null) {
            System.err.println("无法从 application.yml / codeknest-common.yml 解析数据源配置");
            System.exit(2);
        }

        Class.forName(driver);
        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {

            boolean isResultSet = stmt.execute(sql);
            if (isResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int cols = md.getColumnCount();
                    List<String> headers = new ArrayList<>();
                    for (int i = 1; i <= cols; i++) headers.add(md.getColumnLabel(i));
                    System.out.println(String.join("\t", headers));
                    System.out.println(repeat("-", 60));
                    int count = 0;
                    while (rs.next()) {
                        List<String> row = new ArrayList<>();
                        for (int i = 1; i <= cols; i++) {
                            Object val = rs.getObject(i);
                            row.add(val == null ? "NULL" : val.toString());
                        }
                        System.out.println(String.join("\t", row));
                        count++;
                    }
                    System.out.println("\n共 " + count + " 行");
                }
            } else {
                System.out.println(stmt.getUpdateCount() + " 行受影响");
            }
        }
    }

    /**
     * 解析 ${VAR} / ${VAR:default} 占位符：优先取环境变量，其次取默认值。
     * 与 Spring 的宽松解析语义一致，避免把 ${DB_USER} 字面量当用户名去连接。
     */
    private static String resolve(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String env = System.getenv(matcher.group(1));
            String fallback = matcher.group(2);
            String replacement = (env != null && !env.isEmpty()) ? env : (fallback != null ? fallback : "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
