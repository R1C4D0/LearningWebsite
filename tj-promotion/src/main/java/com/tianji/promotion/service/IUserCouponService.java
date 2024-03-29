package com.tianji.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务类
 * </p>
 *
 * @author yzp
 * @since 2024-02-18
 */
public interface IUserCouponService extends IService<UserCoupon> {

    void receiveCoupon(Long couponId);

    /**
     * 兑换码兑换优惠券
     *
     * @param code 兑换码
     */
    void exchangeCoupon(String code);

    void checkAndCreateUserCoupon(Coupon coupon, Long userId, Integer serialNum);
}
