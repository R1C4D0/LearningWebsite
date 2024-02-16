package com.tianji.learning.handler;

import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService pointsBoardSeasonService;

    private final IPointsBoardService pointsBoardService;

    @Scheduled(cron = "0 0 3 1 * ?")//每月1号凌晨3点执行
    public void createPointsBoardTableOfLastSeason() {
        log.debug("开始创建上个赛季的积分榜");
//        1.获取上个月当前时间
        LocalDate time = LocalDate.now().minusMonths(1);
//        2.查询赛季表获取赛季Id 条件是开始时间小于等于上个月当前时间，结束时间大于等于上个月当前时间
        PointsBoardSeason season = pointsBoardSeasonService
                .lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        if (season == null) {
            log.debug("上个赛季不存在");
            return;
        }
        log.debug("上个赛季的赛季Id为{}", season.getId());
//      3.创建上赛季榜单表
        pointsBoardSeasonService.createPointsBoardTableBySeason(season.getId());

    }


}
