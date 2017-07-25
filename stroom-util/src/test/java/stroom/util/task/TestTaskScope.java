/*
 * Copyright 2016 Crown Copyright
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

package stroom.util.task;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import stroom.util.spring.StroomBeanStore;
import stroom.util.spring.StroomSpringProfiles;
import stroom.util.test.ComponentTest;
import stroom.util.test.StroomSpringJUnit4ClassRunner;
import stroom.util.thread.ThreadUtil;

import javax.annotation.Resource;
import java.util.ArrayDeque;

@Category(ComponentTest.class)
@ActiveProfiles(StroomSpringProfiles.TEST)
@RunWith(StroomSpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TaskScopeTestConfiguration.class)
public class TestTaskScope {
    TaskScopeTestSingleton singleton1;
    TaskScopeTestSingleton singleton2;
    Integer num1;
    Integer num2;
    @Resource
    private TaskScopeTestSingleton scopeTestSingleton;
    @Resource
    private StroomBeanStore stroomBeanStore;

    @Test
    public void testThreading() {
        final ArrayDeque<Thread> deque = new ArrayDeque<Thread>();
        for (int i = 0; i < 1; i++) {
            final Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        TaskScopeContextHolder.addContext();

                        ThreadUtil.sleep(10);
                        final TaskScopeTestObject2 threadScopeTestObject2 = new TaskScopeTestObject2();
                        threadScopeTestObject2.setName("CREATED BY TEST");
                        TaskScopeContextHolder.getContext().put("threadScopeTestObject2", threadScopeTestObject2);
                    } catch (final Throwable e) {
                        throw new RuntimeException(e);
                    } finally {
                        TaskScopeContextHolder.removeContext();
                    }
                }
            });
            t.start();
            deque.push(t);
        }
        while (!deque.isEmpty()) {
            ThreadUtil.sleep(10);
            if (!deque.peek().isAlive()) {
                deque.pop();
            }
        }
    }

    @Test
    public void testSingletonScopeBean() {
        TaskScopeTestSingleton singleton1;
        TaskScopeTestSingleton singleton2;

        TaskScopeContextHolder.addContext();
        singleton1 = stroomBeanStore.getBean(TaskScopeTestSingleton.class);
        TaskScopeContextHolder.removeContext();
        TaskScopeContextHolder.addContext();
        singleton2 = stroomBeanStore.getBean(TaskScopeTestSingleton.class);
        TaskScopeContextHolder.removeContext();

        Assert.assertTrue(singleton1 == singleton2);
    }

    @Test
    public void testSingletonScopeBeanTwoThread() {
        singleton1 = null;
        singleton2 = null;
        final Thread t1 = new Thread() {
            @Override
            public void run() {
                TaskScopeContextHolder.addContext();
                singleton1 = stroomBeanStore.getBean(TaskScopeTestSingleton.class);
                TaskScopeContextHolder.removeContext();
            }
        };
        t1.start();
        final Thread t2 = new Thread() {
            @Override
            public void run() {
                TaskScopeContextHolder.addContext();
                singleton2 = stroomBeanStore.getBean(TaskScopeTestSingleton.class);
                TaskScopeContextHolder.removeContext();
            }
        };
        t2.start();

        while (singleton1 == null || singleton2 == null) {
            Assert.assertTrue(ThreadUtil.sleep(100));
        }

        Assert.assertTrue(singleton1 == singleton2);
    }

    @Test
    public void testThreadScopeBean() {
        int num1;
        int num2;

        TaskScopeContextHolder.addContext();
        num1 = stroomBeanStore.getBean(TaskScopeTestObject1.class).getInstanceNumber();
        TaskScopeContextHolder.removeContext();
        TaskScopeContextHolder.addContext();
        num2 = stroomBeanStore.getBean(TaskScopeTestObject1.class).getInstanceNumber();
        TaskScopeContextHolder.removeContext();

        Assert.assertTrue(num1 != num2);

        TaskScopeContextHolder.addContext();
        num1 = stroomBeanStore.getBean(TaskScopeTestObject1.class).getInstanceNumber();
        num2 = stroomBeanStore.getBean(TaskScopeTestObject1.class).getInstanceNumber();
        TaskScopeContextHolder.removeContext();

        Assert.assertTrue(num1 == num2);
    }

    @Test
    public void testThreadScopeBeanTwoThread() {
        final Thread t1 = new Thread() {
            @Override
            public void run() {
                TaskScopeContextHolder.addContext();
                num1 = stroomBeanStore.getBean(TaskScopeTestObject1.class).getInstanceNumber();
                TaskScopeContextHolder.removeContext();
            }
        };
        t1.start();
        final Thread t2 = new Thread() {
            @Override
            public void run() {
                TaskScopeContextHolder.addContext();
                num2 = stroomBeanStore.getBean(TaskScopeTestObject1.class).getInstanceNumber();
                TaskScopeContextHolder.removeContext();
            }
        };
        t2.start();

        while (num1 == null || num2 == null) {
            Assert.assertTrue(ThreadUtil.sleep(100));
        }

        Assert.assertTrue(!num1.equals(num2));
    }
}
