package org.hs.dlm.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

public class RedisCacheComponent {

    private Logger logger = LoggerFactory.getLogger(RedisCacheComponent.class);

    private RedisTemplate redisTemplate;

    private final int DEFAULT_ACQUIRY_RESOLUTION_MILLIS = 100;

    /**
     * Lock key path.
     */
    private String lockKey;

    /**
     * 锁超时时间，防止线程在入锁以后，无限的执行等待
     */
    private int expireMsecs = 60 * 1000;

    /**
     * 锁等待时间，防止线程饥饿
     */
    private int timeoutMsecs = 10 * 1000;

    /**
     * volatile 修饰,强制主存获取值,保证可见性
     */
    private volatile boolean locked = false;

    /**
     * Detailed constructor with default acquire timeout 10000 msecs and lock expiration of 60000 msecs.
     *
     * @param lockKey lock key (ex. account:1, ...)
     */
    public RedisCacheComponent(RedisTemplate redisTemplate, String lockKey) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey + "_lock";
    }

    /**
     * Detailed constructor with default lock expiration of 60000 msecs.
     */
    public RedisCacheComponent(RedisTemplate redisTemplate, String lockKey, int timeoutMsecs) {
        this(redisTemplate, lockKey);
        this.timeoutMsecs = timeoutMsecs;
    }

    /**
     * Detailed constructor.
     */
    public RedisCacheComponent(RedisTemplate redisTemplate, String lockKey, int timeoutMsecs, int expireMsecs) {
        this(redisTemplate, lockKey, timeoutMsecs);
        this.expireMsecs = expireMsecs;
    }

    /**
     * @return lock key
     */
    public String getLockKey() {
        return lockKey;
    }

    private String get(final String key) {
        Object obj = null;
        try {
            obj = redisTemplate.execute((RedisCallback<Object>) connection -> {
                StringRedisSerializer serializer = new StringRedisSerializer();
                byte[] data = connection.get(serializer.serialize(key));
                connection.close();
                return null == data ? null : serializer.deserialize(data);
            });
        } catch (Exception e) {
            logger.info(e.getMessage());
            logger.error("get redis error, key : {}", key);
        }
        return null == obj ? null : obj.toString();
    }

    /**
     * SET if Not eXists(如果不存在，则 SET),若键 key 已经存在， 则 SETNX 命令不做任何动作
     * 返回值：命令在设置成功时返回 1 ，设置失败时返回 0
     *
     * @param key
     * @param value
     * @return
     */
    private boolean setNX(final String key, final String value) {
        Object obj = null;
        try {
            obj = redisTemplate.execute((RedisCallback<Object>) connection -> {
                StringRedisSerializer serializer = new StringRedisSerializer();
                Boolean success = connection.setNX(serializer.serialize(key), serializer.serialize(value));
                connection.close();
                return success;
            });
        } catch (Exception e) {
            logger.info(e.getMessage());
            logger.error("setNX redis error, key : {}", key);
        }
        return null == obj ? false : (Boolean) obj;
    }

    /**
     * 将键 key 的值设为 value ，并返回键 key 在被设置之前的旧的value
     * 返回值：如果键key没有旧值， 也即是说， 键 key 在被设置之前并不存在， 那么命令返回 nil
     * 当键 key 存在但不是字符串类型时，命令返回一个错误
     *
     * @param key
     * @param value
     * @return
     */
    private String getSet(final String key, final String value) {
        Object obj = null;
        try {
            obj = redisTemplate.execute((RedisCallback<Object>) connection -> {
                StringRedisSerializer serializer = new StringRedisSerializer();
                byte[] ret = connection.getSet(serializer.serialize(key), serializer.serialize(value));
                connection.close();
                return serializer.deserialize(ret);
            });
        } catch (Exception e) {
            logger.info(e.getMessage());
            logger.error("setNX redis error, key : {}", key);
        }
        return null == obj ? null : (String) obj;
    }

    /**
     * 获得 lock.
     * 实现思路: 主要是使用了redis的setnx命令,缓存了锁.
     * reids缓存的key是锁的key,所有线程共享, value是锁的到期时间(注意:这里把过期时间放在value了,没有时间上设置其超时时间)
     * 执行过程:
     * 1.通过setnx尝试设置某个key的值,成功(当前没有这个锁)则返回,并获得锁
     * 2.锁已经存在则获取锁的到期时间,和当前时间比较,超时的话,则设置新的值
     *
     * @return true if lock is acquired, false acquire timeouted
     * @throws InterruptedException in case of thread interruption
     */
    public synchronized boolean lock() throws InterruptedException {
        int timeout = timeoutMsecs;
        while (timeout >= 0) {
            long expires = System.currentTimeMillis() + expireMsecs + 1;
            String expiresStr = String.valueOf(expires); //锁到期时间
            if (setNX(lockKey, expiresStr)) {
                // lock acquired
                locked = true;
                return true;
            }

            String currentValueStr = get(lockKey); //redis里的时间
            if (null != currentValueStr && Long.parseLong(currentValueStr) < System.currentTimeMillis()) {
                //判断是否为空，不为空的情况下，如果被其他线程设置了值，则第二个条件判断是过不去的
                // lock is expired

                String oldValueStr = getSet(lockKey, expiresStr);
                //获取上一个锁到期时间，并设置现在的锁到期时间，
                //只有一个线程才能获取上一个线上的设置时间，因为jedis.getSet是同步的
                if (null != oldValueStr && oldValueStr.equals(currentValueStr)) {
                    //防止误删（覆盖，因为key是相同的）了他人的锁——这里达不到效果，这里值会被覆盖，但是因为什么相差了很少的时间，所以可以接受

                    //[分布式的情况下]:如过这个时候，多个线程恰好都到了这里，但是只有一个线程的设置值和当前值相同，他才有权利获取锁
                    // lock acquired
                    locked = true;
                    return true;
                }
            }
            timeout -= DEFAULT_ACQUIRY_RESOLUTION_MILLIS;

            /**
             * 延迟100 毫秒,  这里使用随机时间可能会好一点,可以防止饥饿进程的出现,即当同时到达多个进程,
             * 只会有一个进程获得锁,其他的都用同样的频率进行尝试,后面有来了一些进行,也以同样的频率申请锁,
             * 这将可能导致前面来的锁得不到满足.使用随机的等待时间可以一定程度上保证公平性
             */
            Thread.sleep(DEFAULT_ACQUIRY_RESOLUTION_MILLIS);

        }
        return false;
    }


    /**
     * Acqurired lock release.
     */
    public synchronized void unlock() {
        if (locked) {
            Object obj = null;
            try {
                obj = redisTemplate.execute((RedisCallback<Object>) connection -> {
                    StringRedisSerializer serializer = new StringRedisSerializer();
                    long count = connection.del(serializer.serialize(lockKey));
                    connection.close();
                    return count;
                });
            } catch (Exception e) {
                logger.info(e.getMessage());
                logger.error("delete redis error, key : {}", lockKey);
            }
            //locked为true表明锁释放失败,locked为false表明锁释放成功
            locked = !((Long) obj > 0);
            System.out.println(locked ? "当前还有线程持有锁" : "锁释放成功");
        }

    }
}
