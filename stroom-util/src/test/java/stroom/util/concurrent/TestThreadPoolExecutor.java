/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.concurrent;

import org.junit.jupiter.api.Disabled;

@Disabled
class TestThreadPoolExecutor {
//    @Test
//    public void test() throws Exception {
//        ThreadPoolExecutor threadPoolExecutor =
//        new ThreadPoolExecutor(0, 2, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());
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
//        ThreadPoolExecutor threadPoolExecutor =
//        new ThreadPoolExecutor(0, 2, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
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
//        ThreadPoolExecutor threadPoolExecutor =
//        ScalingThreadPoolExecutor.newScalingThreadPool(0, 2, 60L, TimeUnit.SECONDS);
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
