package com.codeknest.tools;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 轻量 SQL 执行工具。
 * 从 classpath:application.yml 读取数据源配置，执行任意 SQL 并打印结果。
 * 仅用于开发环境调试，不会打包进生产 jar（位于 test 目录）。
 *
 * 用法：
 *   java -cp <classpath> com.codeknest.tools.SqlRunner "SELECT * FROM t_user LIMIT 5"
 */
public class SqlRunner {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("用法: SqlRunner \"<SQL语句>\"");
            System.exit(1);
        }
        String sql = String.join(" ", args);

        String url = null, username = null, password = null, driver = "com.mysql.cj.jdbc.Driver";
        try (InputStream in = SqlRunner.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (in == null) {
                System.err.println("未找到 application.yml");
                System.exit(2);
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
                    if (trimmed.startsWith("url:")) url = trimmed.substring(4).trim();
                    else if (trimmed.startsWith("username:")) username = trimmed.substring(9).trim();
                    else if (trimmed.startsWith("password:")) password = trimmed.substring(9).trim();
                    else if (trimmed.startsWith("driver-class-name:")) driver = trimmed.substring(18).trim();
                }
            }
        }
        if (url == null || username == null) {
            System.err.println("无法从 application.yml 解析数据源配置");
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

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
