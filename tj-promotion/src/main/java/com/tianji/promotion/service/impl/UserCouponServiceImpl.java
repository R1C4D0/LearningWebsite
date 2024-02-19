package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.framework.AopContext;
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

    private final IExchangeCodeService codeService;

    @Override
//    @Transactional//因为涉及到多个数据库操作，所以使用事务
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
        // 4.校验并生成用户券
        Long userId = UserContext.getUser();
        //获取并使用代理对象，以便调用checkAndCreateUserCoupon方法时能够触发事务
        IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
        userCouponService.checkAndCreateUserCoupon(coupon, userId, null);
    }

    /**
     * 校验并持久化用户券到数据库
     *
     * @param coupon    优惠券
     * @param userId    用户ID
     * @param serialNum 兑换码ID
     */
    @MyLock(name = "lock:coupon")//AOP切面基于注解加锁，防止并发问题
    @Transactional // 进事务
    public void checkAndCreateUserCoupon(Coupon coupon, Long userId, Integer serialNum) {
        // 1.校验每人限领数量
        // 1.1.统计当前用户对当前优惠券的已经领取的数量
        Integer count = this.lambdaQuery()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, coupon.getId())
                .count();
        // 1.2.校验限领数量
        if (count != null && count >= coupon.getUserLimit()) {
            throw new BadRequestException("超出领取数量");
        }
        // 2.更新优惠券的已经发放的数量 + 1
        int row = couponMapper.incrIssueNum(coupon.getId());
        if (row == 0) {
            throw new BadRequestException("优惠券库存不足");
        }
        // 3.新增一个用户券
        saveUserCoupon(coupon, userId);
        // 4.如果是兑换码兑换的优惠券，更新兑换码状态
        if (serialNum != null) {
            codeService.lambdaUpdate()
                    .set(ExchangeCode::getUserId, userId)
                    .set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
                    .eq(ExchangeCode::getId, serialNum)
                    .update();
        }
    }

    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        // 1.校验并解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        // 2.查询兑换码对应的优惠券id
        ExchangeCode exchangeCode = codeService.getById(serialNum);
        if (exchangeCode == null) {
            throw new BizIllegalException("兑换码不存在！");
        }
        try {
            // 3.校验是否已经兑换 这里直接执行setbit，通过返回值来判断是否兑换过
            boolean exchanged = codeService.updateExchangeMark(serialNum, true);
            if (exchanged) {
                throw new BizIllegalException("兑换码已经被兑换过了");
            }
            // 4.是否过期
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(exchangeCode.getExpiredTime())) {
                throw new BizIllegalException("兑换码已经过期");
            }
            // 5.校验并生成用户券
            // 5.1.查询优惠券id
            Coupon coupon = couponMapper.selectById(exchangeCode.getExchangeTargetId());
            // 5.2.查询用户
            Long userId = UserContext.getUser();
            // 5.3.校验并生成用户券，更新兑换码状态
            checkAndCreateUserCoupon(coupon, userId, (int) serialNum);
        } catch (Exception e) {
            // 重置兑换的标记 0
            codeService.updateExchangeMark(serialNum, false);
            throw e;
        }
    }

    /**
     * 新增用户优惠券  持久化到数据库
     *
     * @param coupon 优惠券
     * @param userId 用户ID
     */
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
        this.save(uc);
    }
}
