package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author yzp
 * @since 2024-02-15
 */
@Slf4j
@Service
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    @Override
    public void addPointsRecord(SignInMessage message, PointsRecordType type) {
        log.debug("收到消息：{}", message);
//        0. 检验
        if (message.getUserId() == null || message.getPoints() == null) {
            return;
        }
        int realPoints = message.getPoints();
//        1.判断该积分类型是否有上限    type.getMaxPoints() > 0？
        int maxPoints = type.getMaxPoints();
        if (maxPoints > 0) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
            LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);
//            如果有上限 查询当天该用户该类型 今日已获得的积分
            QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
            wrapper.select("sum(points) as totalPoints");
            wrapper.eq("user_id", message.getUserId());
            wrapper.eq("type", type);
            wrapper.between("create_time", dayStartTime, dayEndTime);
            Map<String, Object> map = this.getMap(wrapper);
            int currentPoints = 0;//当前用户    该积分类型   当前已获得的积分
            if (map != null) {
                BigDecimal totalPoints = (BigDecimal) map.get("totalPoints");
                currentPoints = totalPoints.intValue();
            }
            if (currentPoints >= maxPoints) {
                return;
            }
//            如果已获得的积分 + 本次获得的积分 > 上限
            if (currentPoints + message.getPoints() > maxPoints) {
                realPoints = maxPoints - currentPoints;
            }
//            保存积分
            PointsRecord pointsRecord = new PointsRecord();
            pointsRecord.setUserId(message.getUserId());
            pointsRecord.setPoints(realPoints);
            pointsRecord.setType(type);
            log.debug("保存record:{}", pointsRecord);
            this.save(pointsRecord);
        }
    }

    @Override
    public List<PointsStatisticsVO> queryMyTodayPoints() {
        Long userId = UserContext.getUser();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStartTime = DateUtils.getDayStartTime(now);
        LocalDateTime dayEndTime = DateUtils.getDayEndTime(now);

        QueryWrapper<PointsRecord> wrapper = new QueryWrapper<>();
        wrapper.select("type", "sum(points) as userId");//使用UserId字段暂时存储积分
        wrapper.eq("user_id", userId);
        wrapper.between("create_time", dayStartTime, dayEndTime);
        wrapper.groupBy("type");
        List<PointsRecord> recordList = this.list(wrapper);
        if (CollUtils.isEmpty(recordList)) {
            return CollUtils.emptyList();
        }
        //封装VO返回
        List<PointsStatisticsVO> voList = new ArrayList<>();
        for (PointsRecord record : recordList) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(record.getType().getDesc());//积分类型的中文描述
            vo.setMaxPoints(record.getType().getMaxPoints());//积分类型的上限
            vo.setPoints(record.getUserId().intValue());//积分
            voList.add(vo);
        }

        return voList;
    }
}