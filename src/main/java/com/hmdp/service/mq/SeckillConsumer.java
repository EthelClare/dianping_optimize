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

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@Slf4j
public class SeckillConsumer {

    @Autowired
    private IVoucherOrderCreateService voucherOrderCreateService;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void handleSeckillMessage(SeckillMessage message,
                                     Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                     @Header(AmqpHeaders.MESSAGE_ID) String messageId) throws IOException {
        String messageEventId = message.getEventId();
        log.info("begin to handleSeckillMessage: {}, deliveryTag: {}", messageId, deliveryTag);

        try {
            VoucherOrder voucherOrder = VoucherOrder.builder()
                    .build()
                    .setUserId(Long.parseLong(message.getUserId()))
                    .setVoucherId(message.getVoucherId())
                    .setCreateTime(LocalDateTime.now());

            voucherOrderCreateService.createVoucherOrder(voucherOrder);
            log.info("秒杀订单处理成功: {}", messageEventId);
            //发送确认消息 ack
            channel.basicAck(deliveryTag, false);

        } catch (Exception e){
            log.error("handleSeckillMessage error: {}", e.getMessage());
            // 拒绝消息，让消息重新入队，b1给true就能直接重新入队
            channel.basicNack(deliveryTag, false, true);
            log.debug("message will be sended to dlq + {}", messageEventId);
//            channel.basicNack(Long.parseLong(messageEventId), false, false);
        }
    }
}
