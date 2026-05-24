-- Atomic available-stock check and decrement (anti-overselling in Redis).
-- KEYS[1] = inventory:product:{productCode}
-- ARGV[1] = quantity to reserve
-- Returns: new stock on success, -1 if insufficient
local current = tonumber(redis.call('GET', KEYS[1]))
if current == nil then
    return -2
end
local qty = tonumber(ARGV[1])
if current < qty then
    return -1
end
return redis.call('DECRBY', KEYS[1], qty)
