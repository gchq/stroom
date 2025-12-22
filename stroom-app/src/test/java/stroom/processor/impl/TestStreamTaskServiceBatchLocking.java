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

package stroom.processor.impl;


import stroom.node.api.NodeInfo;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

class TestStreamTaskServiceBatchLocking extends AbstractCoreIntegrationTest {

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private ProcessorTaskQueueManager processorTaskQueueManager;
    @Inject
    private NodeInfo nodeInfo;

    private int failedCount = 0;

    private synchronized void setOk(final boolean ok) {
        if (!ok) {
            System.out.println("ONE THREAD FAILED!!!");
            failedCount++;
        }
    }

    @Test
    void test() {
    }


//    @Test
//    public void testMoreLikeTheRealCall() throws InterruptedException {
//
//        if (!entityManager.isMySqlDialect()) {
//            // Can't test this with the in memory DB
//            return;
//        }
//
//        int batchSize = 100;
//        EventFeed efd = commonTestScenarioCreator.createSimpleEventFeed();
//        for (int i = 0; i < batchSize; i++) {
//            commonTestScenarioCreator.createSample2LineRawFile(efd);
//        }
//
//        final FindTranslationStreamTaskCriteria criteria = new
//                FindTranslationStreamTaskCriteria();
//        criteria.setNode(nodeCache.get());
//        criteria.getPageRequest().setLength(2);
//        criteria.setStreamTaskStatus(TaskStatus.UNPROCESSED);
//        criteria.setTranslateFeed(Boolean.TRUE);
//
//        System.out.println("============================");
//
//        ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(3);
//
//        final HashSet<Long> tasksDone = new HashSet<Long>();
//
//        for (int i = 0; i < batchSize; i++) {
//            Thread t = new Thread(new Runnable() {
//
//                @Override
//                public void run() {
//                    try {
//                        BaseResultList<TranslationStreamTask> list = translationStreamTaskService
//                                .assignUnprocessed(criteria);
//
//                        boolean allOk = true;
//                        for (TranslationStreamTask task : list) {
//                            if (tasksDone.contains(task.getId())) {
//                                allOk = false;
//                                System.out.println("FAILED Duplicate "
//                                        + Thread.currentThread().getName()
//                                        + " Done " + task.getId());
//                                setOk(false);
//                            } else {
//                                tasksDone.add(task.getId());
//                                System.out.println("OK "
//                                        + Thread.currentThread().getName()
//                                        + " Done " + task.getId());
//                            }
//                        }
//                        ThreadUtil.sleep(100);
//                        if (allOk) {
//                            setOk(true);
//                        }
//
//                    } catch (final RuntimeException e) {
//                        e.printStackTrace();
//                        setOk(false);
//                    }
//                }
//            });
//            threadPoolExecutor.submit(t);
//        }
//
//        threadPoolExecutor.shutdown();
//
//        threadPoolExecutor.awaitTermination(1000, TimeUnit.SECONDS);
//
//        assertThat(failedCount).as("No threads are expected to fail").isEqualTo(0);
//
//    // }
}
