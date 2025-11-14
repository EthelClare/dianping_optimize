package com.hmdp.service.mq;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.SeckillMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderCreateService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class DlqConsumer {

    @Autowired
    private IVoucherOrderCreateService voucherOrderCreateService;


    @RabbitListener(queues = RabbitMQConfig.DLQ_QUEUE)
    public void handleFailedSeckill(SeckillMessage message,
                                    Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        String messageId = message.getEventId();
        log.warn("处理死信队列消息: {}", messageId);

        try {
            // 1. 先尝试重新处理
            VoucherOrder voucherOrder = VoucherOrder.builder()
                    .id(Long.parseLong(message.getOrderId()))
                    .userId(Long.parseLong(message.getUserId()))
                    .voucherId(message.getVoucherId())
                    .createTime(LocalDateTime.now())
                    .build();

            voucherOrderCreateService.createVoucherOrder(voucherOrder);
            log.info("死信队列消息处理成功: {}", messageId);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("死信队列消息处理失败，需要人工干预: {}", messageId);
            // TODO 这里需要对死信队列中的失败再次处理
        }

    }
}
