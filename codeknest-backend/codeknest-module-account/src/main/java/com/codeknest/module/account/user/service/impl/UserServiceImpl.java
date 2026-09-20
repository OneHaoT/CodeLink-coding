package com.codeknest.module.account.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codeknest.common.core.BusinessException;
import com.codeknest.common.core.ErrorCode;
import com.codeknest.module.account.auth.entity.User;
import com.codeknest.module.account.auth.mapper.UserMapper;
import com.codeknest.module.account.user.dto.UpdateProfileDTO;
import com.codeknest.module.account.user.entity.UserFollow;
import com.codeknest.module.account.user.entity.UserProfile;
import com.codeknest.module.account.user.mapper.UserFollowMapper;
import com.codeknest.module.account.user.mapper.UserProfileMapper;
import com.codeknest.module.account.user.service.UserService;
import com.codeknest.module.account.user.vo.SimpleUserVO;
import com.codeknest.module.account.user.vo.UserHomeVO;
import com.codeknest.module.account.message.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final UserFollowMapper followMapper;
    private final NotificationService notificationService;

    @Override
    public UserHomeVO getUserHome(Long userId, Long currentUserId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserProfile profile = getOrCreateProfile(userId);
        boolean following = currentUserId != null && isFollowing(currentUserId, userId);
        return toVO(user, profile, following);
    }

    @Override
    public UserHomeVO getMyProfile(Long currentUserId) {
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserProfile profile = getOrCreateProfile(currentUserId);
        return toVO(user, profile, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserHomeVO updateMyProfile(Long currentUserId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            String newName = dto.getUsername().trim();
            Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, newName)
                    .ne(User::getId, currentUserId));
            if (count > 0) {
                throw new BusinessException(ErrorCode.USERNAME_EXISTS);
            }
            user.setUsername(newName);
        }
        if (dto.getAvatar() != null) {
            user.setAvatar(dto.getAvatar());
        }
        userMapper.updateById(user);

        UserProfile profile = getOrCreateProfile(currentUserId);
        if (dto.getBio() != null) profile.setBio(dto.getBio());
        if (dto.getWebsite() != null) profile.setWebsite(dto.getWebsite());
        if (dto.getLocation() != null) profile.setLocation(dto.getLocation());
        if (dto.getCompany() != null) profile.setCompany(dto.getCompany());
        if (dto.getGithub() != null) profile.setGithub(dto.getGithub());
        profileMapper.updateById(profile);

        return toVO(user, profile, false);
    }

    @Override
    public List<SimpleUserVO> listFollowers(Long userId) {
        List<UserFollow> rels = followMapper.selectList(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowingId, userId)
                        .orderByDesc(UserFollow::getCreatedAt));
        return toSimpleUsers(rels, UserFollow::getFollowerId);
    }

    @Override
    public List<SimpleUserVO> listFollowing(Long userId) {
        List<UserFollow> rels = followMapper.selectList(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, userId)
                        .orderByDesc(UserFollow::getCreatedAt));
        return toSimpleUsers(rels, UserFollow::getFollowingId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能关注自己");
        }
        User target = userMapper.selectById(followingId);
        if (target == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Long exists = followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFollowingId, followingId));
        if (exists > 0) {
            throw new BusinessException(ErrorCode.ALREADY_FOLLOWED);
        }
        UserFollow rel = new UserFollow();
        rel.setFollowerId(followerId);
        rel.setFollowingId(followingId);
        followMapper.insert(rel);

        getOrCreateProfile(followingId);
        getOrCreateProfile(followerId);
        profileMapper.update(null, new LambdaUpdateWrapper<UserProfile>()
                .eq(UserProfile::getUserId, followingId)
                .setSql("followers_count = followers_count + 1"));
        profileMapper.update(null, new LambdaUpdateWrapper<UserProfile>()
                .eq(UserProfile::getUserId, followerId)
                .setSql("following_count = following_count + 1"));
        notificationService.notify(followingId, NotificationService.NEW_FOLLOWER,
                followerId, null, null, null, "关注了你");
        return getOrCreateProfile(followingId).getFollowersCount();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int unfollow(Long followerId, Long followingId) {
        int deleted = followMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFollowingId, followingId));
        if (deleted > 0) {
            profileMapper.update(null, new LambdaUpdateWrapper<UserProfile>()
                    .eq(UserProfile::getUserId, followingId)
                    .setSql("followers_count = GREATEST(followers_count - 1, 0)"));
            profileMapper.update(null, new LambdaUpdateWrapper<UserProfile>()
                    .eq(UserProfile::getUserId, followerId)
                    .setSql("following_count = GREATEST(following_count - 1, 0)"));
        }
        UserProfile p = profileMapper.selectById(followingId);
        return p != null && p.getFollowersCount() != null ? p.getFollowersCount() : 0;
    }

    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        if (followerId == null || followingId == null) return false;
        return followMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFollowingId, followingId)) > 0;
    }

    @Override
    public void ensureProfile(Long userId) {
        getOrCreateProfile(userId);
    }

    // ==================== private ====================

    private UserProfile getOrCreateProfile(Long userId) {
        UserProfile profile = profileMapper.selectById(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
            profile.setFollowersCount(0);
            profile.setFollowingCount(0);
            profile.setPostsCount(0);
            profileMapper.insert(profile);
        }
        return profile;
    }

    private List<SimpleUserVO> toSimpleUsers(List<UserFollow> rels, Function<UserFollow, Long> idGetter) {
        if (rels.isEmpty()) return Collections.emptyList();
        List<Long> ids = rels.stream().map(idGetter).toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return ids.stream().map(id -> {
            User u = userMap.get(id);
            if (u == null) return null;
            UserProfile p = profileMapper.selectById(id);
            return new SimpleUserVO(u.getId(), u.getUsername(), u.getAvatar(), p != null ? p.getBio() : null);
        }).filter(java.util.Objects::nonNull).toList();
    }

    private UserHomeVO toVO(User user, UserProfile profile, boolean following) {
        return UserHomeVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .bio(profile.getBio())
                .website(profile.getWebsite())
                .location(profile.getLocation())
                .company(profile.getCompany())
                .github(profile.getGithub())
                .followersCount(profile.getFollowersCount())
                .followingCount(profile.getFollowingCount())
                .postsCount(profile.getPostsCount())
                .following(following)
                .build();
    }
}
