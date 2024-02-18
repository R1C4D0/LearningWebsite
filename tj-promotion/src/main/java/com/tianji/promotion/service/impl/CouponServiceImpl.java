package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final IExchangeCodeService codeService;
    private final IUserCouponService userCouponService;


    @Override
    @Transactional
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

    @Override
    @Transactional
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        Integer status = query.getStatus();
        String name = query.getName();
        Integer type = query.getType();
        // 1.分页查询
        Page<Coupon> page = this.lambdaQuery()
                .eq(type != null, Coupon::getDiscountType, type)
                .eq(status != null, Coupon::getStatus, status)
                .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        // 2.处理VO
        List<Coupon> records = page.getRecords();
        if (CollUtils.isEmpty(records)) {
            return PageDTO.empty(page);
        }
        List<CouponPageVO> list = BeanUtils.copyList(records, CouponPageVO.class);
        // 3.返回
        return PageDTO.of(page, list);
    }

    @Override
    @Transactional
    public void beginIssue(CouponIssueFormDTO dto) {
        // 1.查询优惠券
        Coupon coupon = this.getById(dto.getId());
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在！");
        }
        // 2.判断优惠券状态，是否是暂停或待发放
        if (coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE) {
            throw new BizIllegalException("优惠券状态错误！");
        }
        // 3.判断是否是立刻发放
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        boolean isBegin = issueBeginTime == null || !issueBeginTime.isAfter(now);// 是否是立刻发放
        // 4.更新优惠券
        // 4.1.拷贝属性到PO
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        // 4.2.更新状态
        if (isBegin) {
            c.setStatus(CouponStatus.ISSUING);// 发放中
            c.setIssueBeginTime(now);
        } else {
            c.setStatus(CouponStatus.UN_ISSUE);// 待发放
        }
        // 4.3.写入数据库
        this.updateById(c);
        // 5.判断是否需要生成兑换码，优惠券类型必须是兑换码，优惠券状态必须是待发放
        if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
            coupon.setIssueEndTime(c.getIssueEndTime());
            codeService.asyncGenerateCode(coupon);
        }

    }

    @Override
    public List<CouponVO> queryIssuingCoupons() {
        // 1.查询发放中  且 手动领取的优惠券
        List<Coupon> coupons = this.lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if (CollUtils.isEmpty(coupons)) {
            return CollUtils.emptyList();
        }
        // 2.统计当前用户已经领取的优惠券的信息
//        2.0.获取优惠券ID
        List<Long> couponIds = coupons.stream()
                .map(Coupon::getId)
                .collect(Collectors.toList());
        // 2.1.对于上面的优惠券ID，查询当前用户已经领取的优惠券的数据
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();
//        对于上面的优惠券ID，查询当前用户已经领取的优惠券的数量
//        key:优惠券ID  value:已经领取的数量
        Map<Long, Long> issuedMap = userCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 2.3.统计当前用户对优惠券的已经领取并且未使用的数量
        Map<Long, Long> unusedMap = userCoupons.stream()
                .filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 3.封装VO结果
        List<CouponVO> list = coupons.stream()
                .map(coupon -> {
                    // 3.1.拷贝PO属性到VO
                    CouponVO vo = BeanUtils.copyBean(coupon, CouponVO.class);
                    Long issued = issuedMap.getOrDefault(coupon.getId(), 0L);
                    Long unused = unusedMap.getOrDefault(coupon.getId(), 0L);
                    // 3.2.是否可以领取：已经被领取的数量 < 优惠券总数量 && 当前用户已经领取的数量 < 每人限领数量
                    Boolean available =
                            coupon.getIssueNum() < coupon.getTotalNum()
                                    && issued < coupon.getUserLimit();
                    // 3.3.是否可以使用：当前用户已经领取并且未使用的优惠券数量 > 0
                    Boolean usable = unused > 0;
                    vo.setAvailable(available);
                    vo.setReceived(usable);
                    return vo;
                }).collect(Collectors.toList());
        return list;
    }
}
