package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.msg.LikedTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author yzp
 * @since 2024-02-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper rabbitMqHelper;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void addLikeRecord(LikeRecordFormDTO dto) {
//        1.获取当前登录用户
        Long userId = UserContext.getUser();
//        2.判断当前用户是否已经点赞
        boolean flag = dto.getLiked() ? liked(dto, userId) : unliked(dto, userId);
        if (!flag) {//点赞或者取消点赞失败
            return;
        }
//        3.统计该业务的点赞数量,基于redis
//        liked:like:bizId
        String bizId = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();
        Long totalLikeNum = redisTemplate.opsForSet().size(bizId);
        if (totalLikeNum == null) {
            return;
        }
        String bizTypeTotalLikeKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + dto.getBizType();
        redisTemplate.opsForZSet().add(bizTypeTotalLikeKey, dto.getBizId().toString(), totalLikeNum);
//
////        4.发送消息通知到MQ
//        String routingKey = StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, dto.getBizType());
//        LikedTimesDTO msg = new LikedTimesDTO(dto.getBizId(), totalLikeSum);
//        log.debug("发送点赞消息到MQ routingKey:{}", routingKey);
//        rabbitMqHelper.send(
//                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
//                routingKey,
//                msg
//        );
    }

    @Override
    public Set<Long> getLikeStatusByBizIds(Set<Long> bizIds) {
        if (CollUtils.isEmpty(bizIds)) {
            return CollUtils.emptySet();
        }
//      1.获取当前登录用户
        Long userId = UserContext.getUser();
//        2.查询点赞记录表 in bizIds
        List<LikedRecord> list = this.lambdaQuery()
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();
//        3.将List转换为Set
        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());


    }

    private boolean unliked(LikeRecordFormDTO dto, Long userId) {
//      基于redis
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();

        Long result = redisTemplate.opsForSet().remove(key, userId.toString());

        return result != null && result > 0;//操作是否成功

    }

    private boolean liked(LikeRecordFormDTO dto, Long userId) {
//    基于redis做点赞
        String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + dto.getBizId();

        Long result = redisTemplate.opsForSet().add(key, userId.toString());

        return result != null && result > 0;//操作是否成功


    }
}
