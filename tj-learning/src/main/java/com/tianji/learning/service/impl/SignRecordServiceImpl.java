package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;


@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    @Override
    public SignResultVO addSignRecords() {
//        1.签到
//        1.1获取用户id
        Long userId = UserContext.getUser();
//        1.2获取当前日期
        LocalDate now = LocalDate.now();
//        1.3拼接key  sign:uid:userId:yyyyMM
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
//        1.4计算offset
        int offset = now.getDayOfMonth() - 1;
//        1.5保存签到信息到redis
        Boolean exists = redisTemplate.opsForValue().setBit(key, offset, true);
        if (BooleanUtils.isTrue(exists)) {
            throw new BizIllegalException("不允许重复签到");
        }
//        2.计算连续签到天数
        int signDays = countSignDays(key, now.getDayOfMonth());
//        3.计算签到得分
        int rewardPoints = 0;
        switch (signDays) {
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }
//         4.保存积分明细到数据库
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId, rewardPoints + 1));


//        5.封装VO返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    @Override
    public Byte[] querySignRecords() {
        Long userId = UserContext.getUser();
//        拼接key  sign:uid:userId:yyyyMM
        LocalDate now = LocalDate.now();
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX
                + userId
                + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
//      利用redis bitfield命令查询本月第一天到今天的签到记录
        int dayOfMonth = now.getDayOfMonth();
//        bitField命令返回的是十进制 List中只有一个数字
        List<Long> bitFields = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(
                BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(bitFields)) {
            return new Byte[0];
        }
        Long num = bitFields.get(0);
        int offset = dayOfMonth - 1;
//        利用与运算和位移运算得到签到情况
        Byte[] result = new Byte[dayOfMonth];
        while (offset >= 0) {
            result[offset] = (byte) (num & 1);
            num >>>= 1;
            offset--;
        }
        return result;




    }

    /**
     * 计算连续签到天数
     *
     * @param key        redis key
     * @param dayOfMonth 本月第一天到今天的天数
     * @return 连续签到天数
     */
    private int countSignDays(String key, int dayOfMonth) {
//        1.获取本月从1号到今天的签到情况 bitField 得到的是十进制
//        其实只有一个数字，但是返回的是一个list
        List<Long> bitField = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(
                        BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(bitField)) {
            return 0;
        }
        int num = bitField.get(0).intValue();//本月从1号到今天的签到情况，十进制
        // 2.定义一个计数器
        int count = 0;
        // 3.循环，与1做与运算，得到最后一个bit，判断是否为0，为0则终止，为1则继续
        while ((num & 1) == 1) {
            // 4.计数器+1
            count++;
            // 5.把数字右移一位，最后一位被舍弃，倒数第二位成了最后一位
            num >>>= 1;//无符号右移
        }
        return count;
    }
}
