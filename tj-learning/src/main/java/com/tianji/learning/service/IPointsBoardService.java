package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardVO;

import java.util.List;

/**
 * <p>
 * 学霸天梯榜 服务类
 * </p>
 *
 * @author yzp
 * @since 2024-02-15
 */
public interface IPointsBoardService extends IService<PointsBoard> {

    PointsBoardVO queryPointsBoardList(PointsBoardQuery query);

    /**
     * @param key      redis的key
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @return 榜单列表
     */
    List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize);
}
