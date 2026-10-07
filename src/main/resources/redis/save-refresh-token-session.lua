redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
local expiration = redis.call('HPEXPIREAT', KEYS[1], ARGV[3], 'FIELDS', 1, ARGV[1])
return expiration[1]
