-- 1.参数列表
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
local timestamp = ARGV[4]

-- 2.数据key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 3.脚本业务
-- 3.1.判断库存是否充足
local stock = redis.call('get', stockKey)
if not stock or tonumber(stock) <= 0 then
    return {1, '库存不足'}
end

-- 3.2.判断用户是否重复下单
if redis.call('sismember', orderKey, userId) == 1 then
    return {2, '您已购买过此优惠券'}
end

-- 3.3.扣库存
redis.call('decr', stockKey)

-- 3.4.记录用户下单
redis.call('sadd', orderKey, userId)

-- 3.5.生成事件ID（修复随机数）
-- 在Redis Lua中需要先设置随机种子
math.randomseed(tonumber(timestamp))
local randomNum = math.random(1000, 9999)
local eventId = 'seckill:' .. userId .. ':' .. timestamp .. ':' .. randomNum

return {0, eventId}