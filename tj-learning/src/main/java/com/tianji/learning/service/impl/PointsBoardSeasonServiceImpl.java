package com.tianji.learning.service.impl;

import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.mapper.PointsBoardSeasonMapper;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author yzp
 * @since 2024-02-15
 */
@Service
public class PointsBoardSeasonServiceImpl extends ServiceImpl<PointsBoardSeasonMapper, PointsBoardSeason> implements IPointsBoardSeasonService {

    /**
     * 根据赛季id创建积分榜表
     * @param id    赛季id
     */
    @Override
    public void createPointsBoardTableBySeason(Integer id) {
        getBaseMapper().createPointsBoardTableBySeason(LearningConstants.POINTS_BOARD_TABLE_PREFIX + id);
    }
}
