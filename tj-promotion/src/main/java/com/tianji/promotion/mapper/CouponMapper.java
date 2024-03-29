package com.tianji.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.promotion.domain.po.Coupon;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 优惠券的规则信息 Mapper 接口
 * </p>
 *
 * @author yzp
 * @since 2024-02-17
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 更新优惠券的发放数量
     *
     * @param couponId 优惠券id
     * @return 若更新成功则返回1，否则返回0
     */
    @Update("UPDATE coupon SET issue_num = issue_num + 1 WHERE id = #{couponId} AND issue_num < total_num")
    int incrIssueNum(@Param("couponId") Long couponId);
}
