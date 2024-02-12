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
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
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
//        1.获取当前登录用户
        Long userId = UserContext.getUser();
//        2.查询当前用户对bizIds的点赞状态
        Set<Long> likedBizIds = new HashSet<>();
        for (Long bizId : bizIds) {
            Boolean isMember = redisTemplate.opsForSet().isMember(RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId, userId.toString());
            if (isMember != null && isMember) {
                likedBizIds.add(bizId);
            }
        }
        return likedBizIds;



    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {
//        1.拼装key likes:times:type:QA likes:times:type:NOTE
//        读取并移除Redis中缓存的点赞总数
        String bizTypeTotalLikeKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        List<LikedTimesDTO> list = new ArrayList<>();
//        2.从redis的zset结构中，按分数排序取maxBizSize的业务点赞信息
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(bizType, maxBizSize);
        if (CollUtils.isEmpty(tuples)) {
            return;
        }
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String bizId = tuple.getValue();
            Double likeTimes = tuple.getScore();
            if (StringUtils.isBlank(bizId) || likeTimes == null) {
                continue;
            }
//      3.封装LikedTimesDTO作为消息数据
            list.add(new LikedTimesDTO(Long.valueOf(bizId), likeTimes.intValue()));
        }
//        MQ发送消息
        log.debug("批量发送点赞消息 消息内容{}", list);
        rabbitMqHelper.send(
                MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType),
                list
        );
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
