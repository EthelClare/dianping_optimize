package com.hmdp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String SECKILL_EXCHANGE = "seckill_exchange";
    public static final String SECKILL_QUEUE = "seckill_queue";
    public static final String SECKILL_ROUTING_KEY = "seckill";

    //死信交换机和队列
    public static final String DLX_EXCHANGE = "dlx_exchange";
    public static final String DLQ_QUEUE = "dead_letter_queue";
    public static final String DLQ_ROUTING_KEY = "dead";


    @Bean
    public Binding seckillBinding(Queue seckillQueue, DirectExchange seckillExchange) {
        return BindingBuilder.bind(seckillQueue).to(seckillExchange).with(SECKILL_ROUTING_KEY);
    }

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE);
    }

    @Bean
    public Queue seckillQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);    // 死信交换器
        args.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY); // 死信路由键
        args.put("x-message-ttl", 60000); // 消息过期时间（可选，单位毫秒）
        return new Queue(SECKILL_QUEUE, true, false, false, args);
    }

    // 死信队列配置
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue(DLQ_QUEUE, true);
    }

    @Bean
    public Binding dlxBinding(Queue dlxQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlxQueue).to(dlxExchange).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
