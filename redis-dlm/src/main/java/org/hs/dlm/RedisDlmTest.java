package org.hs.dlm;

import org.hs.dlm.component.RedisCacheComponent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedisDlmTest {


    public static void main(String[] args) {

        ApplicationContext context = new ClassPathXmlApplicationContext("beans.xml");

        RedisTemplate redisTemplate = (RedisTemplate) context.getBean("redisTemplate");


        RedisCacheComponent redisCacheComponent = new RedisCacheComponent(redisTemplate, "buss_opt", 1000, 2000);

        int nThreads = 5;

        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(nThreads);

        for (int i = 0; i < nThreads; i++) {
            fixedThreadPool.submit(() -> {
                Long currentThreadId = Thread.currentThread().getId();
                try {
                    //尝试获取锁
                    if (redisCacheComponent.lock()) {
                        System.out.println("线程" + currentThreadId + "获取锁成功");
                        System.out.println("线程" + currentThreadId + "开始处理业务。。。");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    //释放锁
                    redisCacheComponent.unlock();
                    System.out.println("线程" + currentThreadId + "释放锁");
                }
            });
        }


    }


}
