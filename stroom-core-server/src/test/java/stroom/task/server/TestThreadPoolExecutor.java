package stroom.task.server;

import org.junit.Ignore;
import org.junit.Test;
import stroom.util.concurrent.ScalingThreadPoolExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Ignore
public class TestThreadPoolExecutor {
//    @Test
//    public void test() throws Exception {
//        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(0, 2, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
//
//        final CountDownLatch countDownLatch = new CountDownLatch(100);
//        for (int i = 0; i < 100; i++) {
//            threadPoolExecutor.execute(() -> {
//                try {
//                    Thread.sleep(1000);
//
//                    countDownLatch.countDown();
//                } catch (final InterruptedException e) {
//                    throw new RuntimeException(e.getMessage(), e);
//                }
//            });
//        }
//
//        countDownLatch.await();
//        System.out.println(threadPoolExecutor.getLargestPoolSize());
//    }
//
//    @Test
//    public void test2() throws Exception {
//        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(0, 2, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
//
//        final CountDownLatch countDownLatch = new CountDownLatch(100);
//        for (int i = 0; i < 100; i++) {
//            threadPoolExecutor.execute(() -> {
//                try {
//                    Thread.sleep(1000);
//                    countDownLatch.countDown();
//                } catch (final InterruptedException e) {
//                    throw new RuntimeException(e.getMessage(), e);
//                }
//            });
//        }
//
//        countDownLatch.await();
//        System.out.println(threadPoolExecutor.getLargestPoolSize());
//    }
//
//    @Test
//    public void test3() throws Exception {
//        ThreadPoolExecutor threadPoolExecutor = ScalingThreadPoolExecutor.newScalingThreadPool(0, 2, 60L, TimeUnit.SECONDS);
//
//        final CountDownLatch countDownLatch = new CountDownLatch(100);
//        for (int i = 0; i < 100; i++) {
//            threadPoolExecutor.execute(() -> {
//                try {
//                    Thread.sleep(1000);
//                    countDownLatch.countDown();
//                } catch (final InterruptedException e) {
//                    throw new RuntimeException(e.getMessage(), e);
//                }
//            });
//        }
//
//        countDownLatch.await();
//        System.out.println(threadPoolExecutor.getLargestPoolSize());
//    }
//
//    @Test
//    public void test4() throws Exception {
//        ThreadPoolExecutor threadPoolExecutor = new ScalingThreadPoolExecutor2(0, 2, 60L, TimeUnit.SECONDS);
//
//        final CountDownLatch countDownLatch = new CountDownLatch(100);
//        for (int i = 0; i < 100; i++) {
//            threadPoolExecutor.execute(() -> {
//                try {
//                    Thread.sleep(1000);
//                    countDownLatch.countDown();
//                } catch (final InterruptedException e) {
//                    throw new RuntimeException(e.getMessage(), e);
//                }
//            });
//        }
//
//        countDownLatch.await();
//        System.out.println(threadPoolExecutor.getLargestPoolSize());
//    }
}
