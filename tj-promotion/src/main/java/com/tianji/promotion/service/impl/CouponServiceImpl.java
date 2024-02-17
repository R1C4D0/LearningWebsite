package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author yzp
 * @since 2024-02-17
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService couponScopeService;


    @Override
    public void saveCoupon(CouponFormDTO dto) {
        // 1.保存优惠券到数据库
        // 1.1.转PO
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        // 1.2.保存
        this.save(coupon);
        if (!dto.getSpecific()) {
            // 没有范围限定
            return;
        }
//        有范围限定
        Long couponId = coupon.getId();// 优惠券ID
        // 2.保存限定范围
        List<Long> scopes = dto.getScopes();
        if (CollUtils.isEmpty(scopes)) {
            throw new BadRequestException("限定范围不能为空");
        }
        // 2.1.转换PO
        List<CouponScope> list = scopes.stream().
                map(bizId -> {
                    CouponScope couponScope = new CouponScope();
                    couponScope.setCouponId(couponId);// 优惠券ID
                    couponScope.setBizId(bizId);// 业务ID
                    return couponScope;
                }).collect(Collectors.toList());
        // 2.2.保存
        couponScopeService.saveBatch(list);
    }
}
