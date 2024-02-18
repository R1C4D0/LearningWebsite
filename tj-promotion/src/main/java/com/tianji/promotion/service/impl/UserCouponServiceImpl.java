package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author yzp
 * @since 2024-02-18
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
    //因为在CouponServiceImpl中使用了IUserCouponService，所以这里使用CouponMapper防止循环依赖
    private final CouponMapper couponMapper;

    @Override
    @Transactional
    public void receiveCoupon(Long couponId) {
        // 1.查询优惠券
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 2.校验发放时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
            throw new BadRequestException("优惠券发放已经结束或尚未开始");
        }
        // 3.校验库存
        if (coupon.getIssueNum() >= coupon.getTotalNum()) {
            throw new BadRequestException("优惠券库存不足");
        }
        // 4.校验每人限领数量是否超限
        // 4.1.统计当前用户对当前优惠券的已经领取的数量
        Long userId = UserContext.getUser();
        Integer count = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId)
                .count();
        // 4.2.校验限领数量
        if (count != null && count >= coupon.getUserLimit()) {
            throw new BadRequestException("超出领取数量");
        }
        // 5.更新优惠券的已经发放的数量 + 1
        couponMapper.incrIssueNum(couponId);
        // 6.新增一个用户券记录
        saveUserCoupon(coupon, userId);


    }

    private void saveUserCoupon(Coupon coupon, Long userId) {
        // 1.基本信息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        // 2.有效期信息
        LocalDateTime termBeginTime = coupon.getTermBeginTime();//优惠券的有效期开始时间
        LocalDateTime termEndTime = coupon.getTermEndTime();//优惠券的有效期结束时间
        if (termBeginTime == null) {//如果没有设置优惠券的有效期开始时间，则使用有效期天数计算
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);
        // 3.保存
        save(uc);
    }
}
