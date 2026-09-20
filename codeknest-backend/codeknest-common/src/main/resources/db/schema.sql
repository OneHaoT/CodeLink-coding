-- ============================================
-- CodeLink 表结构 — 应用启动时由 spring.sql.init 自动执行
-- 语义：只建缺失对象（CREATE TABLE IF NOT EXISTS），已存在的表一律不碰
-- 注意：本文件是表结构的唯一来源；scripts/init.sql 仅用于清空重建
-- 不含 DROP DATABASE / CREATE DATABASE / USE —— 连接已指向目标库
-- ============================================

-- ============================================
-- 1. 用户模块
-- ============================================

CREATE TABLE IF NOT EXISTS t_user (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    email           VARCHAR(128)    NULL                    COMMENT '登录邮箱（可选，支持昵称注册）',
    password        VARCHAR(255)    NOT NULL                COMMENT 'BCrypt密码',
    username        VARCHAR(32)     NOT NULL                COMMENT '显示昵称',
    avatar          VARCHAR(512)    NULL                    COMMENT '头像URL',
    status          TINYINT         NOT NULL DEFAULT 1      COMMENT '状态 1=正常 0=禁用',
    role            VARCHAR(32)     NOT NULL DEFAULT 'ROLE_USER' COMMENT '角色',
    last_login_at   DATETIME        NULL                    COMMENT '最后登录时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email (email),
    UNIQUE KEY uk_username (username),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='用户账号表';

CREATE TABLE IF NOT EXISTS t_user_profile (
    user_id             BIGINT UNSIGNED NOT NULL            COMMENT '用户ID',
    bio                 VARCHAR(255)    NULL                COMMENT '个人简介',
    website             VARCHAR(512)    NULL                COMMENT '个人网站',
    location            VARCHAR(128)    NULL                COMMENT '所在地',
    company             VARCHAR(128)    NULL                COMMENT '公司',
    github              VARCHAR(128)    NULL                COMMENT 'GitHub昵称',
    followers_count     INT UNSIGNED    NOT NULL DEFAULT 0  COMMENT '粉丝数',
    following_count     INT UNSIGNED    NOT NULL DEFAULT 0  COMMENT '关注数',
    posts_count         INT UNSIGNED    NOT NULL DEFAULT 0  COMMENT '文章数',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES t_user(id)
) ENGINE=InnoDB COMMENT='用户详情表';

CREATE TABLE IF NOT EXISTS t_user_follow (
    follower_id     BIGINT UNSIGNED NOT NULL COMMENT '发起关注者',
    following_id    BIGINT UNSIGNED NOT NULL COMMENT '被关注者',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (follower_id, following_id),
    INDEX idx_following (following_id),
    CONSTRAINT fk_follower  FOREIGN KEY (follower_id)  REFERENCES t_user(id),
    CONSTRAINT fk_following FOREIGN KEY (following_id) REFERENCES t_user(id)
) ENGINE=InnoDB COMMENT='关注关系表';

-- ============================================
-- 2. 文章模块
-- ============================================

CREATE TABLE IF NOT EXISTS t_category (
    id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL,
    slug        VARCHAR(64)  NOT NULL,
    description VARCHAR(255) NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name),
    UNIQUE KEY uk_slug (slug)
) ENGINE=InnoDB COMMENT='分类表';

CREATE TABLE IF NOT EXISTS t_tag (
    id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(32)  NOT NULL,
    slug        VARCHAR(64)  NOT NULL,
    post_count  INT UNSIGNED NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name),
    UNIQUE KEY uk_slug (slug)
) ENGINE=InnoDB COMMENT='标签表';

CREATE TABLE IF NOT EXISTS t_post (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    category_id     INT UNSIGNED    NULL,
    title           VARCHAR(200)    NOT NULL,
    summary         VARCHAR(500)    NULL,
    content         LONGTEXT        NOT NULL,
    cover_image     VARCHAR(512)    NULL,
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '0=已删除 1=已发布 2=待审核 3=审核拒绝',
    view_count      INT UNSIGNED    NOT NULL DEFAULT 0,
    like_count      INT UNSIGNED    NOT NULL DEFAULT 0,
    comment_count   INT UNSIGNED    NOT NULL DEFAULT 0,
    favorite_count  INT UNSIGNED    NOT NULL DEFAULT 0,
    published_at    DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_user_status (user_id, status),
    INDEX idx_category_status (category_id, status),
    INDEX idx_status_created (status, created_at DESC),
    INDEX idx_published (published_at DESC),
    CONSTRAINT fk_post_user     FOREIGN KEY (user_id)     REFERENCES t_user(id),
    CONSTRAINT fk_post_category FOREIGN KEY (category_id) REFERENCES t_category(id)
) ENGINE=InnoDB COMMENT='文章主表';

CREATE TABLE IF NOT EXISTS t_post_tag (
    post_id BIGINT UNSIGNED NOT NULL,
    tag_id  INT UNSIGNED    NOT NULL,
    PRIMARY KEY (post_id, tag_id),
    CONSTRAINT fk_pt_post FOREIGN KEY (post_id) REFERENCES t_post(id),
    CONSTRAINT fk_pt_tag  FOREIGN KEY (tag_id)  REFERENCES t_tag(id)
) ENGINE=InnoDB COMMENT='文章-标签关联表';

-- 自阶段 5 起草稿已迁移至 MongoDB（post_draft 集合），本表保留不删
CREATE TABLE IF NOT EXISTS t_post_draft (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    post_id     BIGINT UNSIGNED NULL,
    title       VARCHAR(200)    NULL,
    content     LONGTEXT        NULL,
    summary     VARCHAR(500)    NULL,
    cover_image VARCHAR(512)    NULL,
    tag_names   VARCHAR(255)    NULL COMMENT '标签名称，逗号分隔',
    category_id INT UNSIGNED    NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user (user_id, updated_at DESC),
    CONSTRAINT fk_draft_user FOREIGN KEY (user_id) REFERENCES t_user(id)
) ENGINE=InnoDB COMMENT='草稿表（已弃用，草稿存 MongoDB）';

-- ============================================
-- 3. 评论模块
-- ============================================

CREATE TABLE IF NOT EXISTS t_comment (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id             BIGINT UNSIGNED NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    parent_id           BIGINT UNSIGNED NULL,
    reply_to_user_id    BIGINT UNSIGNED NULL,
    content             VARCHAR(2000)   NOT NULL,
    like_count          INT UNSIGNED    NOT NULL DEFAULT 0,
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '0=已删除 1=正常 2=待审核',
    created_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_post_parent (post_id, parent_id, created_at DESC),
    INDEX idx_user (user_id, created_at DESC),
    CONSTRAINT fk_comment_post       FOREIGN KEY (post_id)          REFERENCES t_post(id),
    CONSTRAINT fk_comment_user       FOREIGN KEY (user_id)          REFERENCES t_user(id),
    CONSTRAINT fk_comment_parent     FOREIGN KEY (parent_id)        REFERENCES t_comment(id),
    CONSTRAINT fk_comment_reply_user FOREIGN KEY (reply_to_user_id) REFERENCES t_user(id)
) ENGINE=InnoDB COMMENT='评论表';

CREATE TABLE IF NOT EXISTS t_comment_like (
    user_id    BIGINT UNSIGNED NOT NULL,
    comment_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, comment_id),
    CONSTRAINT fk_cl_user    FOREIGN KEY (user_id)    REFERENCES t_user(id),
    CONSTRAINT fk_cl_comment FOREIGN KEY (comment_id) REFERENCES t_comment(id)
) ENGINE=InnoDB COMMENT='评论点赞表';

-- ============================================
-- 4. 互动模块
-- ============================================

CREATE TABLE IF NOT EXISTS t_post_like (
    user_id    BIGINT UNSIGNED NOT NULL,
    post_id    BIGINT UNSIGNED NOT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, post_id),
    INDEX idx_post (post_id),
    CONSTRAINT fk_pl_user FOREIGN KEY (user_id) REFERENCES t_user(id),
    CONSTRAINT fk_pl_post FOREIGN KEY (post_id) REFERENCES t_post(id)
) ENGINE=InnoDB COMMENT='文章点赞表';

CREATE TABLE IF NOT EXISTS t_post_favorite (
    user_id    BIGINT UNSIGNED NOT NULL,
    post_id    BIGINT UNSIGNED NOT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, post_id),
    INDEX idx_post (post_id),
    CONSTRAINT fk_pf_user FOREIGN KEY (user_id) REFERENCES t_user(id),
    CONSTRAINT fk_pf_post FOREIGN KEY (post_id) REFERENCES t_post(id)
) ENGINE=InnoDB COMMENT='文章收藏表';

-- ============================================
-- 5. 消息通知模块
-- ============================================

CREATE TABLE IF NOT EXISTS t_notification (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           BIGINT UNSIGNED NOT NULL              COMMENT '接收者',
    type              VARCHAR(32)     NOT NULL              COMMENT 'LIKE_POST/LIKE_COMMENT/COMMENT_POST/REPLY_COMMENT/NEW_FOLLOWER/SYSTEM',
    title             VARCHAR(200)    NULL,
    content           VARCHAR(500)    NULL,
    source_user_id    BIGINT UNSIGNED NULL,
    source_post_id    BIGINT UNSIGNED NULL,
    source_comment_id BIGINT UNSIGNED NULL,
    is_read           TINYINT(1)      NOT NULL DEFAULT 0,
    read_at           DATETIME        NULL,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_read (user_id, is_read, created_at DESC),
    INDEX idx_source_user (source_user_id),
    CONSTRAINT fk_notify_user      FOREIGN KEY (user_id)           REFERENCES t_user(id),
    CONSTRAINT fk_notify_source    FOREIGN KEY (source_user_id)    REFERENCES t_user(id)
) ENGINE=InnoDB COMMENT='通知消息表';

-- ============================================
-- 6. RBAC 权限表（管理员）
-- ============================================

CREATE TABLE IF NOT EXISTS t_role (
    id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(32)  NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB COMMENT='角色表';

CREATE TABLE IF NOT EXISTS t_permission (
    id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB COMMENT='权限表';

CREATE TABLE IF NOT EXISTS t_user_role (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id INT UNSIGNED    NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES t_user(id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES t_role(id)
) ENGINE=InnoDB COMMENT='用户-角色关联表';

CREATE TABLE IF NOT EXISTS t_role_permission (
    role_id       INT UNSIGNED NOT NULL,
    permission_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES t_role(id),
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES t_permission(id)
) ENGINE=InnoDB COMMENT='角色-权限关联表';

-- ============================================
-- 7. 管理辅助表
-- ============================================

CREATE TABLE IF NOT EXISTS t_audit_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NULL,
    action      VARCHAR(64)     NOT NULL,
    target      VARCHAR(255)    NULL,
    ip          VARCHAR(64)     NULL,
    detail      TEXT            NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user (user_id),
    INDEX idx_action (action),
    INDEX idx_created (created_at DESC)
) ENGINE=InnoDB COMMENT='操作审计日志';

CREATE TABLE IF NOT EXISTS t_sensitive_word (
    id         INT UNSIGNED NOT NULL AUTO_INCREMENT,
    word       VARCHAR(64)  NOT NULL,
    category   VARCHAR(32)  NULL COMMENT 'sensitive/ad/political',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_word (word)
) ENGINE=InnoDB COMMENT='敏感词表';

-- ============================================
-- 8. 社区公告
-- ============================================

CREATE TABLE IF NOT EXISTS t_notice (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    title      VARCHAR(100) NOT NULL COMMENT '公告标题',
    content    VARCHAR(1000) NOT NULL COMMENT '公告内容',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1=已发布 0=下架',
    sort_order INT          NOT NULL DEFAULT 0 COMMENT '排序权重，越大越靠前',
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_status_sort (status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区公告';
