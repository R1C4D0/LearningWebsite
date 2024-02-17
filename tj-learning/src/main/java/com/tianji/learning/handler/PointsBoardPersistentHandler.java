package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService pointsBoardSeasonService;
    //  注入必须使用接口注入  若采取实现类注入时，当实现类使用@Transational注解时使用代理对象，代理对象强转兄弟类时会报错
    private final IPointsBoardService pointsBoardService;

    private final StringRedisTemplate stringRedisTemplate;

//    @Scheduled(cron = "0 0 3 1 * ?")//每月1号凌晨3点执行

    /**
     * 创建上个赛季的积分榜
     */
    @XxlJob("createTableJob")
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

    /**
     * 持久化上个赛季（上个月）的积分排行榜数据到数据库
     * fixme 由于xxl-job的分片机制，导致后续任务链的执行时机不可控，待修复
     */
    @XxlJob("savePointsBoard2DB")//任务名字要和xxljob控制台的JobHandler名字一致
    public void savePointsBoard2DB() {
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);

        // 2.计算动态表名
        // 2.1.查询赛季信息
        PointsBoardSeason season = pointsBoardSeasonService
                .lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, time)
                .ge(PointsBoardSeason::getEndTime, time)
                .one();
        if (season == null) {
            log.debug("上个赛季不存在");
            return;
        }
        Integer seasonId = season.getId();
        log.debug("上个赛季的赛季Id为{}", seasonId);
        // 2.2.将表名存入ThreadLocal
        TableInfoContext.setInfo(LearningConstants.POINTS_BOARD_TABLE_PREFIX + seasonId);

        // 3.查询榜单数据
        // 3.1.拼接KEY
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 3.2.查询数据
        int index = XxlJobHelper.getShardIndex();//分片序号,从0开始
        int total = XxlJobHelper.getShardTotal();//分片总数
        int pageNo = index + 1; // 起始页，就是分片序号+1
        int pageSize = 10;
        while (true) {
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(boardList)) {//跳出循环
                break;
            }
            // 4.持久化到数据库
            // 4.1.把排名信息写入id
            boardList.forEach(b -> {
                b.setId(b.getRank().longValue());//历史赛季排行榜中id为排名
                b.setRank(null);//排名置空，否则会报错，因为rank字段在历史赛季排行榜中是不存在的
            });
            // 4.2.持久化到数据库
            pointsBoardService.saveBatch(boardList);
            // 5.翻页,使用分布式任务时，需要分页查询，否则会导致数据量过大，内存溢出，每次跳过total页
            pageNo += total;
        }
        // 任务结束，移除动态表名(ThreadLocal中的信息)
        TableInfoContext.remove();
    }

    /**
     * 清理Redis缓存中的上个月积分榜数据
     */
    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis() {
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 3.删除,使用unlink命令删除，不会阻塞，不会返回删除的结果,异步删除
        stringRedisTemplate.unlink(key);
    }


}
