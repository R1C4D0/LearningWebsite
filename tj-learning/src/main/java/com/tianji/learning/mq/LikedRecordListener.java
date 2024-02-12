package com.tianji.learning.mq;


import com.tianji.api.dto.msg.LikedTimesDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikedRecordListener {

    private final IInteractionReplyService interactionReplyService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "qa.liked.times.queue", durable = "true"),
            exchange = @Exchange(value = MqConstants.Exchange.LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE))
    public void onMsg(LikedTimesDTO msg) {
        log.debug("LikedRecordListener.onMsg msg:{}", msg);
        InteractionReply reply = interactionReplyService.getById(msg.getBizId());
        if (reply == null) {
            log.warn("点赞消息处理失败，回复不存在 bizId:{}", msg.getBizId());
            return;
        }
        reply.setLikedTimes(msg.getLikedTimes());
        interactionReplyService.updateById(reply);
    }

}

