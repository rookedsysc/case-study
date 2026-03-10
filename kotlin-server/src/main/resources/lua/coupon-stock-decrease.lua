local remaining = tonumber(redis.call('GET', KEYS[1]))
if not remaining or remaining <= 0 then
    return 0
end
redis.call('DECR', KEYS[1])
return 1
