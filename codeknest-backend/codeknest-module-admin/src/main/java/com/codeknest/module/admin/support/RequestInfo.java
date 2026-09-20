package com.codeknest.module.admin.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求信息工具（IP 解析）
 */
public final class RequestInfo {

    private RequestInfo() {}

    /** 解析当前请求的客户端 IP；无请求上下文（异步场景）返回 null */
    public static String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            String ip = firstIp(req.getHeader("X-Forwarded-For"));
            if (ip == null) ip = req.getHeader("X-Real-IP");
            if (ip == null) ip = req.getRemoteAddr();
            return ip;
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstIp(String header) {
        if (header == null || header.isBlank() || "unknown".equalsIgnoreCase(header.trim())) return null;
        String ip = header.split(",")[0].trim();
        return ip.isBlank() ? null : ip;
    }
}
