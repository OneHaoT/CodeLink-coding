-- ============================================
-- CodeLink 种子数据 — 应用启动时由 spring.sql.init 自动执行
-- 语义：缺失即补，已存在不动（不覆盖部署者修改过的数据）
--   唯一键冲突 → ON DUPLICATE KEY UPDATE <主键> = <主键>（空更新，不做任何修改）
--   无唯一键的表（t_notice）→ 用 WHERE NOT EXISTS 按标题判重
-- 注意：每次启动都会执行，故所有语句必须幂等
-- ============================================

-- 默认分类（uk_name / uk_slug 冲突时空更新）
INSERT INTO t_category (name, slug, description, sort_order) VALUES
    ('后端开发',   'backend',    'Java / Go / Python / Node.js 等后端技术', 1),
    ('前端开发',   'frontend',   'Vue / React / Angular / 小程序',          2),
    ('数据库',     'database',   'MySQL / Redis / MongoDB / ES',            3),
    ('DevOps',     'devops',     'Docker / K8s / CI/CD / 云原生',           4),
    ('架构设计',   'architecture','分布式 / 微服务 / 高可用',                 5),
    ('人工智能',   'ai',         '机器学习 / 深度学习 / LLM',               6),
    ('程序员日常', 'life',       '职场 / 面试 / 学习笔记',                   7)
ON DUPLICATE KEY UPDATE id = id;

-- 默认标签（uk_name / uk_slug 冲突时空更新）
INSERT INTO t_tag (name, slug) VALUES
    ('Spring Boot','spring-boot'), ('Spring Cloud','spring-cloud'),
    ('Java','java'), ('Vue 3','vue3'), ('React','react'),
    ('MySQL','mysql'), ('Redis','redis'), ('MongoDB','mongodb'),
    ('Elasticsearch','elasticsearch'), ('RabbitMQ','rabbitmq'),
    ('Docker','docker'), ('Kubernetes','kubernetes'),
    ('面试','interview'), ('算法','algorithm'), ('项目实战','project')
ON DUPLICATE KEY UPDATE id = id;

-- 默认角色（uk_name 冲突时空更新）
INSERT INTO t_role (name, description) VALUES
    ('ROLE_USER',      '普通用户'),
    ('ROLE_MODERATOR', '内容审核员'),
    ('ROLE_ADMIN',     '超级管理员')
ON DUPLICATE KEY UPDATE id = id;

-- 默认管理员账号（用户名: admin / 密码: admin — BCrypt 加密）
-- !!! 仅用于开发环境首次启动；正式部署后请立即登录后台修改密码 !!!
-- 冲突时（uk_username / uk_email）空更新，不会重置已改过的密码
INSERT INTO t_user (email, password, username, avatar, status, role) VALUES
    ('admin@codeknest.com',
     '$2a$10$jAAaGqMTs1axZJWB9D7S9OyfNcNt9weoNYvMocSpv1CHNKyBQxDkm',
     'admin', NULL, 1, 'ROLE_ADMIN')
ON DUPLICATE KEY UPDATE id = id;

-- 管理员详情与角色关联（不写死 user_id，兼容库非空场景）
INSERT IGNORE INTO t_user_profile (user_id)
    SELECT id FROM t_user WHERE username = 'admin';

INSERT IGNORE INTO t_user_role (user_id, role_id)
    SELECT u.id, r.id FROM t_user u JOIN t_role r ON r.name = 'ROLE_ADMIN'
    WHERE u.username = 'admin';

-- 社区公告（无唯一键，按标题判重，避免每次启动重复插入）
INSERT INTO t_notice (title, content, sort_order)
SELECT s.title, s.content, s.sort_order
FROM (
    SELECT 'CodeLink v1.0 正式上线，欢迎反馈' AS title,
           'CodeLink v1.0 正式上线，欢迎各位开发者反馈建议。' AS content, 100 AS sort_order
    UNION ALL SELECT 'IK 分词器已就位，全文搜索秒级响应',
           '全文搜索已接入 IK 分词，中文搜索更精准。', 90
    UNION ALL SELECT '全站已关闭 HTTPS，请放心访问',
           '当前环境未启用 HTTPS，访问一切正常。', 80
) s
WHERE NOT EXISTS (SELECT 1 FROM t_notice n WHERE n.title = s.title);
