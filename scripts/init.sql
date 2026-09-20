-- ============================================
-- CodeLink 数据库重置脚本（仅用于「清空重来」）
-- ============================================
-- 表结构、索引与种子数据的唯一来源在应用内：
--     codeknest-backend/codeknest-common/src/main/resources/db/schema.sql
--     codeknest-backend/codeknest-common/src/main/resources/db/data.sql
-- 应用启动时会自动执行它们（幂等：缺失即补，已存在不动）。
--
-- 因此本脚本只负责「删库」，不再包含任何 DDL / 种子数据，避免两份定义日久失同步。
--
-- 使用方式（删除后重启应用即自动重建库、表与种子数据）：
--     1) 先停掉 codeknest-server-web 与 codeknest-server-admin
--        （否则 HikariCP 连接池会持有已删除库的连接）
--     2) 执行本脚本：.\run-sql.ps1 "DROP DATABASE IF EXISTS codeknest"
--     3) 重新启动应用，观察日志中的自举过程
-- ============================================

DROP DATABASE IF EXISTS codeknest;
