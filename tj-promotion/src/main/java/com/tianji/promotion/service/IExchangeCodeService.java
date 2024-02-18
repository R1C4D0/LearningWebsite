package com.tianji.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author yzp
 * @since 2024-02-17
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

    void asyncGenerateCode(Coupon coupon);

    /**
     * 更新兑换码的兑换状态
     *
     * @param serialNum 兑换码序列号
     * @param mark      兑换状态
     * @return 更新成功返回true
     */
    boolean updateExchangeMark(long serialNum, boolean mark);
}
