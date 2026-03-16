local current = tonumber(redis.call('GET', KEYS[1]) or 0)
if current > 0 then
    redis.call('DECRBY', KEYS[1], 1)
    return 1
end
return 0
