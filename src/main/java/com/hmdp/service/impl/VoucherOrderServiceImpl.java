package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;


    private static final DefaultRedisScript<List> SECKILL_SCRIPT;

    // 初始化的时候就将seckill.lua 加载到DefaultRedisScript中
    static {
        // 在类加载时初始化脚本，只加载一次
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(List.class);
    }



//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public Result seckillVoucher(Long voucherId) {
//        long startTime = System.currentTimeMillis();
////        Long userId = UserHolder.getUser().getId(); TODO 为了测试写死了
//        Long userId = 1L;
//        long orderId = redisIdWorker.nextId("order");
//        // 1.执行lua脚本
//        log.info("开始执行秒杀脚本 - 用户: {}, 优惠券: {}, 订单: {}", userId, voucherId, orderId);
//
//        List<Object> result = Collections.singletonList(stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString(), String.valueOf(orderId),
//                String.valueOf(System.currentTimeMillis())
//        ));
//        log.info(result.toString());
//
//        // 2.判断结果
//        if (result == null || result.size() < 2) {
//            return Result.fail("秒杀失败");
//        }
//
//        int code = ((Number) result.get(0)).intValue();
//        String message = (String) result.get(1);
//        // 2.判断结果是否为0
//        if(code == 0){
//            String eventId = message;  // 第二个元素就是eventId
//            SeckillMessage seckillMessage = new SeckillMessage(userId.toString(),
//                    String.valueOf(orderId), eventId, voucherId);
//            sendMessageToRabbitMQ(seckillMessage);
//            log.info("秒杀成功 - 用户: {}, 商品: {}, 耗时: {}ms",
//                    userId, orderId, System.currentTimeMillis() - startTime);
//            // 3.返回订单id
//            return Result.ok(orderId);
//            //TODO 待处理
//        }else{
//            // 2.1.不为0 ，代表没有购买资格
//            return Result.fail( (String)result.get(1));
//        }
//
    //    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result seckillVoucher(Long voucherId) {
        long startTime = System.currentTimeMillis();
        Long userId = 1L; // 测试用固定用户
        long orderId = redisIdWorker.nextId("order");

        try {
            log.info("=== 开始秒杀流程 ===");
            log.info("用户ID: {}, 优惠券ID: {}, 生成的订单ID: {}", userId, voucherId, orderId);
            log.info("时间戳: {}", System.currentTimeMillis());

            // 1.执行lua脚本 - 关键修改：去掉Collections.singletonList包装
            log.info("开始执行Lua脚本...");

            Object rawResult = stringRedisTemplate.execute(
                    SECKILL_SCRIPT,
                    Collections.emptyList(),
                    voucherId.toString(),
                    userId.toString(),
                    String.valueOf(orderId),
                    String.valueOf(System.currentTimeMillis())
            );

            // 详细的调试信息
            log.info("=== Lua脚本执行结果调试信息 ===");
            log.info("原始结果类型: {}", rawResult != null ? rawResult.getClass().getName() : "null");
            log.info("原始结果值: {}", rawResult);

            if (rawResult == null) {
                log.error("Lua脚本返回null，可能的原因：");
                log.error("1. Redis连接失败");
                log.error("2. 脚本语法错误");
                log.error("3. 脚本执行异常");
                return Result.fail("系统繁忙，请稍后重试");
            }

            // 检查结果类型
            if (!(rawResult instanceof List)) {
                log.error("脚本返回类型错误，期望List，实际类型: {}", rawResult.getClass().getName());
                log.error("实际返回值: {}", rawResult);
                return Result.fail("系统异常");
            }

            List<Object> result = (List<Object>) rawResult;
            log.info("转换后的List结果: {}", result);
            log.info("List大小: {}", result.size());

            // 2.判断结果
            if (result.isEmpty()) {
                log.error("脚本返回空列表");
                return Result.fail("秒杀失败");
            }

            if (result.size() < 2) {
                log.error("脚本返回结果长度不足，期望至少2个元素，实际: {}", result.size());
                log.error("实际内容: {}", result);
                return Result.fail("系统异常");
            }

            // 调试每个元素
            for (int i = 0; i < result.size(); i++) {
                Object element = result.get(i);
                log.info("结果[{}]: 类型={}, 值={}", i,
                        element != null ? element.getClass().getSimpleName() : "null",
                        element);
            }

            // 解析结果
            Object codeObj = result.get(0);
            Object messageObj = result.get(1);

            log.info("codeObj类型: {}, 值: {}",
                    codeObj != null ? codeObj.getClass().getSimpleName() : "null", codeObj);
            log.info("messageObj类型: {}, 值: {}",
                    messageObj != null ? messageObj.getClass().getSimpleName() : "null", messageObj);

            if (!(codeObj instanceof Number)) {
                log.error("第一个元素不是数字类型: {}", codeObj);
                return Result.fail("系统异常");
            }

            int code = ((Number) codeObj).intValue();
            String message = messageObj != null ? messageObj.toString() : "未知错误";

            log.info("解析结果 - 代码: {}, 消息: {}", code, message);

            // 2.判断结果是否为0
            if (code == 0) {
                String eventId = message;
                log.info("秒杀成功，生成事件ID: {}", eventId);

                SeckillMessage seckillMessage = new SeckillMessage(
                        userId.toString(),
                        String.valueOf(orderId),
                        eventId,
                        voucherId
                );

                // 发送消息到RabbitMQ
                log.info("发送消息到RabbitMQ: {}", seckillMessage);
                sendMessageToRabbitMQ(seckillMessage);

                long endTime = System.currentTimeMillis();
                log.info("秒杀成功 - 用户: {}, 优惠券: {}, 订单: {}, 总耗时: {}ms",
                        userId, voucherId, orderId, endTime - startTime);

                return Result.ok(orderId);
            } else {
                log.warn("秒杀失败 - 代码: {}, 原因: {}", code, message);
                return Result.fail(message);
            }

        } catch (Exception e) {
            log.error("秒杀过程发生异常", e);
            return Result.fail("系统繁忙，请稍后重试");
        }
    }


    private void sendMessageToRabbitMQ(SeckillMessage seckillMessage) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_EXCHANGE,  // 指定交换机名称
                    RabbitMQConfig.SECKILL_ROUTING_KEY, // 指定路由键
                    seckillMessage,
                    msg -> {
                        msg.getMessageProperties().setExpiration("30000");
                        // 设置messageId
                        msg.getMessageProperties().setMessageId(seckillMessage.getEventId());
                        return msg;
                    }
            );
            log.info("已发送消息到RabbitMQ:----------待处理");
        } catch (AmqpException e) {
            log.error("发送消息到RabbitMQ失败：: {}", e.getMessage());
            //TODO 待优化有回滚的操作
        }
    }

}

//@Resource
//private RedissonClient redissonClient;

//    @Resource
//    private RedisTemplate<String, Object> redisTemplate;
//    @Resource
//    private ISeckillVoucherService seckillVoucherService;
//创建的是单线程的线程池
//private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();


/**
 * 这里的 PostConstruct 是一个关键且重要的地方
 */
//    @PostConstruct
//    private void init() {
//        createConsumerGroup();
////        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }


//    private void createConsumerGroup() {
//        try {
//            // 尝试创建消费者组，如果已存在会抛出异常，我们捕获并忽略
//            stringRedisTemplate.opsForStream().createGroup("stream.orders", ReadOffset.latest(), "g1");
//            log.info("创建消费者组 g1 成功");
//        } catch (Exception e) {
//            // 消费者组可能已存在，忽略这个错误
//            log.info("消费者组 g1 可能已存在: {}", e.getMessage());
//        }
//    }




//    private class VoucherOrderHandler implements Runnable {
//
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
//                    );
//                    // 2.判断订单信息是否为空
//                    if (list == null || list.isEmpty()) {
//                        // 如果为null，说明没有消息，继续下一次循环
//                        continue;
//                    }
//                    // 解析数据
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> value = record.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//                    // 3.创建订单
//                    createVoucherOrder(voucherOrder);
//                    // 4.确认消息 XACK
//                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                    handlePendingList();
//                }
//            }
//        }
//
//        private void handlePendingList() {
//            while (true) {
//                try {
//                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1),
//                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
//                    );
//                    // 2.判断订单信息是否为空
//                    if (list == null || list.isEmpty()) {
//                        // 如果为null，说明没有异常消息，结束循环
//                        break;
//                    }
//                    // 解析数据
//                    MapRecord<String, Object, Object> record = list.get(0);
//                    Map<Object, Object> value = record.getValue();
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
//                    // 3.创建订单
//                    createVoucherOrder(voucherOrder);
//                    // 4.确认消息 XACK
//                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//    }


/**
 * 用来创建订单
 * @param voucherOrder
 */
//    private void createVoucherOrder(VoucherOrder voucherOrder) {
//        Long userId = voucherOrder.getUserId();
//        Long voucherId = voucherOrder.getVoucherId();
//        // 创建锁对象
//        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
//        // 尝试获取锁
//        boolean isLock = redisLock.tryLock();
//        // 判断
//        if (!isLock) {
//            // 获取锁失败，直接返回失败或者重试
//            log.error("不允许重复下单！");
//            return;
//        }
//
//        try {
//            // 5.1.查询订单
//            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//            // 5.2.判断是否存在
//            if (count > 0) {
//                // 用户已经购买过了
//                log.error("不允许重复下单！");
//                return;
//            }
//
//            // 6.扣减库存
//            boolean success = seckillVoucherService.update()
//                    .setSql("stock = stock - 1") // set stock = stock - 1
//                    .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
//                    .update();
//            if (!success) {
//                // 扣减失败
//                log.error("库存不足！");
//                return;
//            }
//
//            // 7.创建订单
//            save(voucherOrder);
//        } finally {
//            // 释放锁
//            redisLock.unlock();
//        }
//    }

