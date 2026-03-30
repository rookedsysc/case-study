local current = redis.call('GET', KEYS[1])
if not current then
    return -1
end

current = tonumber(current)
if current > 0 then
    redis.call('DECRBY', KEYS[1], 1)
    return 1
end

return 0
