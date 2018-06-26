/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

public class TestByteBufferPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteBufferPool.class);

    @Test
    public void doWithBuffer() {

        ByteBufferPool byteBufferPool = new ByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);
        int minCapacity = 100;
        byteBufferPool.doWithBuffer(minCapacity, buffer -> {

            assertThat(buffer).isNotNull();
            assertThat(buffer.isDirect()).isTrue();
            assertThat(buffer.capacity()).isEqualTo(minCapacity);

            assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        });

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);
    }

    @Test
    public void doWithBuffer_differentSize() {

        ByteBufferPool byteBufferPool = new ByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        //will create a new buffer
        byteBufferPool.release(byteBufferPool.getBuffer(10));

        //will use the 10 buffer
        byteBufferPool.release(byteBufferPool.getBuffer(1));

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        //will create a new buffer
        byteBufferPool.release(byteBufferPool.getBuffer(1000));

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);

        int minCapacity = 100;
        int expectedCapacity = 1000;
        byteBufferPool.doWithBuffer(minCapacity, buffer -> {

            assertThat(buffer).isNotNull();
            assertThat(buffer.isDirect()).isTrue();
            assertThat(buffer.capacity()).isEqualTo(expectedCapacity);

            assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        });

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);
    }

    @Test
    public void doWithBufferPair_differentSize() {

        ByteBufferPool byteBufferPool = new ByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        //will create two new buffers
        byteBufferPool.release(byteBufferPool.getBufferPair(10, 11));

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);

        //will use the 10 and 11 buffers
        byteBufferPool.release(byteBufferPool.getBufferPair(1, 2));

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);

        //will create two new buffers
        byteBufferPool.release(byteBufferPool.getBufferPair(1000, 1001));

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(4);

        int minKeyCapacity = 100;
        int minValueCapacity = 101;
        int expectedKeyCapacity = 1000;
        int expectedValueCapacity = 1001;
        byteBufferPool.doWithBufferPair(minKeyCapacity, minValueCapacity, bufferPair -> {
            assertThat(bufferPair).isNotNull();
            assertThat(bufferPair.getKeyBuffer().isDirect()).isTrue();
            assertThat(bufferPair.getKeyBuffer().capacity()).isEqualTo(expectedKeyCapacity);
            assertThat(bufferPair.getValueBuffer().isDirect()).isTrue();
            assertThat(bufferPair.getValueBuffer().capacity()).isEqualTo(expectedValueCapacity);

            // count is two less as our buffers are out of the pool
            assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);
        });

        // our buffers are back in the pool
        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(4);
    }

    @Test
    public void testBufferClearing() {
        ByteBufferPool byteBufferPool = new ByteBufferPool();

        ByteBuffer byteBuffer = byteBufferPool.getBuffer(10);

        byteBuffer.putLong(Long.MAX_VALUE);

        assertThat(byteBuffer.position()).isEqualTo(8);
        assertThat(byteBuffer.capacity()).isEqualTo(10);

        byteBufferPool.release(byteBuffer);

        ByteBuffer byteBuffer2 = byteBufferPool.getBuffer(10);

        // got same instance from pool
        assertThat(byteBuffer2).isSameAs(byteBuffer);

        // buffer has been cleared
        assertThat(byteBuffer.position()).isEqualTo(0);
        assertThat(byteBuffer.capacity()).isEqualTo(10);
    }

    @Test
    public void testConcurrency() throws InterruptedException {
        int threadCount = 50;
        int minCapacity = 10;
        final ByteBufferPool byteBufferPool = new ByteBufferPool();

        assertPoolSizeAfterMultipleConcurrentGetRequests(threadCount, minCapacity, byteBufferPool);

        //re-run the same thing and the pool size should be the same at the end
        assertPoolSizeAfterMultipleConcurrentGetRequests(threadCount, minCapacity, byteBufferPool);
    }

    private void assertPoolSizeAfterMultipleConcurrentGetRequests(final int threadCount, final int minCapacity, final ByteBufferPool byteBufferPool) {
        final CountDownLatch countDownLatch = new CountDownLatch(threadCount);
        final ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {

            completableFutures.add(CompletableFuture.runAsync(() -> {

                ByteBuffer byteBuffer = byteBufferPool.getBuffer(minCapacity);
                countDownLatch.countDown();
//                LOGGER.debug("latch count {}", countDownLatch.getCount());

                try {
                    // wait for all threads to have got a new buffer from the pool
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted", e);
                }
                byteBufferPool.release(byteBuffer);
            }, executorService));
        }

        completableFutures.forEach(completableFuture -> {
            try {
                completableFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(threadCount);

        completableFutures.clear();
    }
}