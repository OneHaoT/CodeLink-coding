package com.codeknest.common.init;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * 部署自举：JWT 签名密钥的自动生成与注入（免手填，克隆即跑）。
 *
 * <p><b>为什么需要它</b>：JWT 是对称签名，密钥由项目自己签发、自己验签，
 * 值本身没有外部约束（不像数据库密码必须与 MySQL 里的账号一致）。
 * 但 {@code codeknest-common.yml} 里写的是 {@code ${JWT_SECRET}} 且无默认值，
 * 未配置时占位符无法解析导致启动直接失败 —— 对刚克隆仓库的人来说是白屏式卡点。
 * 本类在配置绑定之前把密钥补进 Environment，使「不填也能跑」。
 *
 * <p><b>取值优先级</b>：
 * <ol>
 *   <li>环境变量 {@code JWT_SECRET}（生产推荐显式配置）</li>
 *   <li>本地密钥文件（默认 {@code ./data/.jwt-secret}，可用 {@code JWT_SECRET_FILE} 改路径）</li>
 *   <li>以上都没有：生成 48 字节随机密钥并写入该文件，后续启动复用</li>
 * </ol>
 * 落盘复用而非每次随机，是为了保证<b>重启后已签发的 token 不失效</b>，
 * 以及同一工作目录下 web / admin 两个 starter 共用同一密钥。
 * 多机部署时各机生成不同密钥会导致 token 互不认，此时应显式配置 {@code JWT_SECRET}
 * 或把 {@code JWT_SECRET_FILE} 指向共享卷。
 *
 * <p><b>执行顺序</b>：本类不声明高优先级，取默认的
 * {@link Ordered#LOWEST_PRECEDENCE}，晚于 {@code ConfigDataEnvironmentPostProcessor}，
 * 因此能读到配置文件里可能存在的 {@code JWT_SECRET} / {@code JWT_SECRET_FILE}。
 * 配置绑定发生在上下文刷新阶段，远晚于此，故注入一定先于 {@code JwtProperties} 绑定。
 *
 * <p><b>日志</b>：EPP 执行时日志系统尚未初始化，必须用 Boot 注入的
 * {@link DeferredLogFactory} 取 Log（消息先缓存，上下文就绪后统一重放）；
 * commons-logging 的 {@link Log} 不支持 {@code {}} 占位符，只能字符串拼接。
 */
public class JwtSecretBootstrapPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** 与 codeknest-common.yml 中 codeknest.jwt.secret 的占位符同名 */
    private static final String SECRET_KEY = "JWT_SECRET";

    /** 自定义本地密钥文件路径（相对工作目录或绝对路径） */
    private static final String FILE_KEY = "JWT_SECRET_FILE";

    private static final String DEFAULT_SECRET_FILE = "./data/.jwt-secret";

    /** HS256 要求密钥至少 256 位，即 32 字节 */
    private static final int MIN_LENGTH = 32;

    /** 生成 48 字节（384 位）随机密钥，Base64 后为 64 个字符，留出余量 */
    private static final int GENERATED_BYTES = 48;

    private static final String PROPERTY_SOURCE_NAME = "codeknestJwtSecret";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Log log;

    public JwtSecretBootstrapPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(getClass());
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configured = environment.getProperty(SECRET_KEY);
        if (isUsable(configured)) {
            if (configured.trim().length() < MIN_LENGTH) {
                // 显式配置了却不合法：直接失败，避免带着弱密钥跑起来
                throw new IllegalStateException("JWT_SECRET 长度不足：" + (MIN_LENGTH * 8) + " 位签名算法要求至少 "
                        + MIN_LENGTH + " 个字符，当前只有 " + configured.trim().length() + " 个字符。"
                        + "请改用更长的随机串（如 openssl rand -base64 48 生成），"
                        + "或删除该配置由应用自动生成。");
            }
            // 用户显式配置，最高优先级，不做任何干预
            return;
        }

        String rawPath = environment.getProperty(FILE_KEY);
        String displayPath = StringUtils.hasText(rawPath) && !rawPath.contains("${") ? rawPath : DEFAULT_SECRET_FILE;
        Path secretFile = Path.of(displayPath);

        String secret = readSecret(secretFile);
        if (secret != null) {
            log.info("JWT 密钥：未配置环境变量，复用本地密钥文件 " + displayPath);
        } else {
            secret = generateSecret();
            boolean saved = writeSecret(secretFile, secret);
            if (saved) {
                // 并发启动时可能被另一实例抢先写入，以文件内容为准，保证两实例同一密钥
                String written = readSecret(secretFile);
                if (written != null) {
                    secret = written;
                }
                log.info("JWT 密钥：未配置环境变量，已自动生成并写入 " + displayPath
                        + "（后续启动复用，不会因重启使已登录用户掉线；生产环境建议显式配置 JWT_SECRET）");
            } else {
                log.warn("JWT 密钥：未配置环境变量且本地密钥文件不可写，本次使用临时随机密钥（重启后已登录用户将掉线）");
            }
        }

        environment.getPropertySources().addFirst(
                new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(SECRET_KEY, secret)));
    }

    @Override
    public int getOrder() {
        // 晚于 ConfigDataEnvironmentPostProcessor（HIGHEST_PRECEDENCE + 10），确保能读到配置文件中的取值
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** 已配置 = 非空且占位符已被解析（未注入的环境变量会保留 ${VAR} 字面量） */
    private static boolean isUsable(String value) {
        return StringUtils.hasText(value) && !value.contains("${");
    }

    /** 读取并校验本地密钥文件；不存在、不可读或长度不足均返回 null */
    private String readSecret(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (content.length() < MIN_LENGTH) {
                log.warn("JWT 密钥：本地密钥文件内容过短，将重新生成");
                return null;
            }
            return content;
        } catch (IOException e) {
            log.warn("JWT 密钥：本地密钥文件读取失败，将重新生成（" + e.getMessage() + "）");
            return null;
        }
    }

    /**
     * 原子写入密钥文件：先写同目录临时文件再移动，
     * 避免两个 starter 同时启动时读到一个写了一半的文件。
     */
    private boolean writeSecret(Path file, String secret) {
        try {
            Path absolute = file.toAbsolutePath();
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(absolute)) {
                // 已在读文件之后被其他实例写入，交由调用方重新读取
                return true;
            }
            Path temp = Files.createTempFile(parent, ".jwt-secret", ".tmp");
            try {
                Files.writeString(temp, secret, StandardCharsets.UTF_8);
                Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
            return true;
        } catch (IOException e) {
            // 落盘失败不影响本次启动：仍用本次生成的密钥，只是重启后会变
            log.warn("JWT 密钥：本地密钥文件写入失败（" + e.getMessage() + "）");
            return false;
        }
    }

    private static String generateSecret() {
        byte[] bytes = new byte[GENERATED_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}