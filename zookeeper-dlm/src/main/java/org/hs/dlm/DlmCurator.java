package org.hs.dlm;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 1、客户端连接zookeeper，并在/lock下创建临时的且有序的子节点，第一个客户端对应的子节点为/lock/lock-0000000000，第二个为/lock/lock-0000000001，以此类推；
 * 2、客户端获取/lock下的子节点列表，判断自己创建的子节点是否为当前子节点列表中 序号最小 的子节点，如果是则认为获得锁，否则监听/lock的子节点变更消息，获得子节点变更通知后重复此步骤直至获得锁；
 * 3、执行业务代码；
 * 4、完成业务流程后，删除对应的子节点释放锁。
 * <p>
 * 创建的节点在zookeeper里结构如下所示：
 * * /curator
 * *  +-- lock
 * *     +------ _c_2e0e248c-f179-4a04-8e7f-483867fe1403-lock-0000000001
 * *     +------ _c_2e0e248c-f179-4a04-8e7f-483867fe1403-lock-0000000002
 * *     +------ _c_2e0e248c-f179-4a04-8e7f-483867fe1403-lock-0000000003
 * *     +------ _c_2e0e248c-f179-4a04-8e7f-483867fe1403-lock-0000000004
 */
public class DlmCurator {

    //可以多个 127.0.0.1:2181,127.0.0.1:2182,...
    private static final String address = "127.0.0.1:2181";

    public static void main(String[] args) {
        //1、重试策略：初试时间为1s 重试3次
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        //2、通过工厂创建连接
        CuratorFramework client = CuratorFrameworkFactory.newClient(address, retryPolicy);
        //3、开启连接
        client.start();
        //4 分布式锁
        final InterProcessMutex mutex = new InterProcessMutex(client, "/curator/lock");
        //读写锁
        //InterProcessReadWriteLock readWriteLock = new InterProcessReadWriteLock(client, "/readwriter");

        int nThreads = 5;

        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(nThreads);

        for (int i = 0; i < nThreads; i++) {
            fixedThreadPool.submit(new Runnable() {
                @Override
                public void run() {
                    boolean flag = false;
                    try {
                        //尝试获取锁，最多等待5秒
                        flag = mutex.acquire(5, TimeUnit.SECONDS);
                        Thread currentThread = Thread.currentThread();
                        System.out.println(mutex.getParticipantNodes());
                        System.out.println("线程" + currentThread.getId() + "获取锁:" + flag);
                        //模拟业务逻辑，延时4秒
                        Thread.sleep(4000);
                        System.out.println("线程" + currentThread.getId() + "开始处理业务。。。");
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        //释放锁
                        if (flag) {
                            try {
                                mutex.release();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        }
    }
}
