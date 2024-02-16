package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author yzp
 * @since 2024-02-15
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;

    private final UserClient userClient;

    @Override
    public PointsBoardVO queryPointsBoardList(PointsBoardQuery query) {
//        1.判断是否查询当前赛季
        Long season = query.getSeason();
        boolean isCurrent = season == null || season == 0;
        //拼接redis的key
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 2.查询我的积分和排名
        PointsBoard myBoard = isCurrent ?
                queryMyCurrentBoard(key) : // 查询当前榜单（Redis）
                queryMyHistoryBoard(season); // 查询历史榜单（MySQL）
//        3.查询榜单列表
        List<PointsBoard> list = isCurrent ?
                queryCurrentBoardList(key, query.getPageNo(), query.getPageSize()) :
                queryHistoryBoardList(query);
//         4.封装VO返回
        PointsBoardVO vo = new PointsBoardVO();
        // 4.1.处理我的信息
        if (myBoard != null) {
            vo.setPoints(myBoard.getPoints());
            vo.setRank(myBoard.getRank());
        }
        if (CollUtils.isEmpty(list)) {
            return vo;
        }
//        4.2查询榜单列表的用户信息    使用feign调用用户微服务
        Set<Long> uIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toSet());
        List<UserDTO> userDTOS = userClient.queryUserByIds(uIds);
//      转Map    key：用户id    value:用户名称
        Map<Long, String> collect = new HashMap<>(uIds.size());
        if (CollUtils.isNotEmpty(userDTOS)) {
            collect = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        }
        List<PointsBoardItemVO> itemVOList = new ArrayList<>(list.size());
        for (PointsBoard pointsBoard : list) {
            PointsBoardItemVO itemVO = new PointsBoardItemVO();
            itemVO.setPoints(pointsBoard.getPoints());
            itemVO.setRank(pointsBoard.getRank());
            itemVO.setName(collect.get(pointsBoard.getUserId()));
            itemVOList.add(itemVO);
        }
        vo.setBoardList(itemVOList);
        return vo;


    }

    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery query) {
//        TODO
        return null;
    }

    /**
     * @param key      redis的key
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @return 榜单列表
     */
    private List<PointsBoard> queryCurrentBoardList(String key, Integer pageNo, Integer pageSize) {
//        1. 计算分页
        int from = (pageNo - 1) * pageSize;
        int end = from + pageSize - 1;
//        2.查询
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, from, end);
//        3.封装返回
        if (CollUtils.isEmpty(tuples)) {
            return CollUtils.emptyList();
        }
        int rank = from + 1;
        List<PointsBoard> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String userId = tuple.getValue();
            Double points = tuple.getScore();
            if (userId == null || points == null) {
                continue;
            }
            PointsBoard board = new PointsBoard();
            board.setUserId(Long.valueOf(userId));
            board.setPoints(points.intValue());
            board.setRank(rank++);
            list.add(board);
        }
        return list;


    }


    private PointsBoard queryMyHistoryBoard(Long season) {
//        TODO
        return null;
    }

    private PointsBoard queryMyCurrentBoard(String key) {
//        1.绑定zset操作    key：redis的key
        BoundZSetOperations<String, String> ops = redisTemplate.boundZSetOps(key);
//        2.查询当前用户的排名和积分
        String userId = UserContext.getUser().toString();//当前用户id
        Double points = ops.score(userId);//积分
        Long reverseRank = ops.reverseRank(userId);//从大到小的排名,从0开始
        PointsBoard pointsBoard = new PointsBoard();
        pointsBoard.setPoints(points == null ? 0 : points.intValue());
        pointsBoard.setRank(reverseRank == null ? 0 : reverseRank.intValue() + 1);//排名  从1开始
        return pointsBoard;
    }
}
