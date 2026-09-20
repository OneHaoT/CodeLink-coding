package com.codeknest.module.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codeknest.module.admin.mapper.StatisticsMapper;
import com.codeknest.module.admin.service.StatisticsService;
import com.codeknest.module.admin.vo.OverviewVO;
import com.codeknest.module.admin.vo.TrendPointVO;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.content.comment.entity.Comment;
import com.codeknest.module.content.comment.mapper.CommentMapper;
import com.codeknest.module.content.post.entity.Post;
import com.codeknest.module.content.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final StatisticsMapper statisticsMapper;

    @Override
    public OverviewVO overview() {
        LocalDate today = LocalDate.now();

        OverviewVO vo = new OverviewVO();
        vo.setUserCount(userMapper.selectCount(null));
        vo.setPostCount(postMapper.selectCount(
                new LambdaQueryWrapper<Post>().eq(Post::getStatus, 1)));
        vo.setCommentCount(commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().eq(Comment::getStatus, 1)));
        vo.setTotalViews(statisticsMapper.sumPostViews());

        vo.setTodayNewUsers(userMapper.selectCount(
                new LambdaQueryWrapper<User>().ge(User::getCreatedAt, today.atStartOfDay())));
        vo.setTodayNewPosts(postMapper.selectCount(new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, 1)
                .ge(Post::getCreatedAt, today.atStartOfDay())));
        vo.setPendingPosts(postMapper.selectCount(
                new LambdaQueryWrapper<Post>().eq(Post::getStatus, 2)));
        return vo;
    }

    @Override
    public List<TrendPointVO> trends(Integer daysReq) {
        int days = daysReq == null ? 7 : Math.min(Math.max(daysReq, 1), 30);
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1L);

        Map<LocalDate, Long> users = toDailyMap(statisticsMapper.dailyUsers(start.atStartOfDay()));
        Map<LocalDate, Long> posts = toDailyMap(statisticsMapper.dailyPosts(start.atStartOfDay()));
        Map<LocalDate, Long> comments = toDailyMap(statisticsMapper.dailyComments(start.atStartOfDay()));

        return start.datesUntil(today.plusDays(1))
                .map(d -> new TrendPointVO(
                        d.toString(),
                        users.getOrDefault(d, 0L),
                        posts.getOrDefault(d, 0L),
                        comments.getOrDefault(d, 0L)))
                .toList();
    }

    private Map<LocalDate, Long> toDailyMap(List<Map<String, Object>> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> row : rows) {
            Object d = row.get("d");
            Object c = row.get("c");
            if (d == null || c == null) continue;
            map.put(LocalDate.parse(d.toString()), ((Number) c).longValue());
        }
        return map;
    }
}
