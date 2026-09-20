package com.codeknest.module.content.post.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.module.content.post.entity.SensitiveWord;
import com.codeknest.module.content.post.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 敏感词校验组件。
 * <p>
 * 前台（server-web）与后台（server-admin）共用：文章发布/编辑、评论发布时调用
 * {@link #check(String...)} 拦截命中敏感词的内容。
 * <p>
 * 匹配策略：加载全部敏感词做包含匹配（忽略大小写），命中多个只回显第一个；
 * 词表为空时不阻断任何内容，避免误伤。
 */
@Component
@RequiredArgsConstructor
public class SensitiveWordChecker {

    private final SensitiveWordMapper wordMapper;

    /** 校验文本是否包含敏感词，命中则抛业务异常 */
    public void check(String... texts) {
        List<SensitiveWord> words = wordMapper.selectList(new LambdaQueryWrapper<>());
        if (words.isEmpty() || texts == null) {
            return;
        }
        for (SensitiveWord sw : words) {
            String word = sw.getWord();
            if (!StringUtils.hasText(word)) {
                continue;
            }
            for (String text : texts) {
                if (StringUtils.hasText(text) && text.toLowerCase().contains(word.toLowerCase())) {
                    throw new BusinessException(ErrorCode.SENSITIVE_WORD_BLOCKED,
                            "内容包含敏感词：" + word);
                }
            }
        }
    }
}