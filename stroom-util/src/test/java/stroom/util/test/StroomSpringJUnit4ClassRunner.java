/*
 * Copyright 2017 Crown Copyright
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

package stroom.util.test;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.task.TaskScopeContextHolder;

import java.io.File;

public class StroomSpringJUnit4ClassRunner extends SpringJUnit4ClassRunner {
    private final boolean cacheSpringContext = true;

    public StroomSpringJUnit4ClassRunner(final Class<?> clazz) throws InitializationError {
        super(clazz);
    }

    @Override
    protected TestClass createTestClass(final Class<?> testClass) {
        return super.createTestClass(testClass);
    }

    @Override
    protected Object createTest() throws Exception {
        return super.createTest();
    }

    /**
     * Creates a new {@link TestContextManager} for the supplied test class.
     * <p>
     * Can be overridden by subclasses.
     *
     * @param clazz the test class to be managed
     */
    @Override
    protected TestContextManager createTestContextManager(final Class<?> clazz) {
        return new TestContextManager(clazz) {
            @Override
            public void beforeTestClass() throws Exception {
                super.beforeTestClass();
            }

            @Override
            public void afterTestClass() throws Exception {
                // If we aren't caching the Spring context them mark it dirty so
                // it is destroyed.
                if (!cacheSpringContext) {
                    final TestContext testContext = getTestContext();
                    testContext.markApplicationContextDirty(HierarchyMode.EXHAUSTIVE);
                    testContext.setAttribute(DependencyInjectionTestExecutionListener.REINJECT_DEPENDENCIES_ATTRIBUTE,
                            Boolean.TRUE);
                }

                super.afterTestClass();
            }
        };
    }

    @Override
    public void run(final RunNotifier notifier) {
        try {
            if (!cacheSpringContext) {
                TestState.getState().destroy();
            }
            TestState.getState().create();

            printTemp();
            super.run(notifier);
            printTemp();

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void printTemp() {
        try {
            final File dir = FileUtil.getTempDir();
            if (dir != null) {
                System.out.println("TEMP DIR = " + dir.getCanonicalPath());
            } else {
                System.out.println("TEMP DIR = NULL");
            }
        } catch (final Exception e) {
        }
    }

    @Override
    protected void runChild(final FrameworkMethod method, final RunNotifier notifier) {
        try {
            try {
                TaskScopeContextHolder.addContext();

                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                try {
                    StroomJUnit4ClassRunner.runChildBefore(this, method, notifier);
                    super.runChild(method, notifier);

                } finally {
                    StroomJUnit4ClassRunner.runChildAfter(this, method, notifier, logExecutionTime);
                }

            } finally {
                TaskScopeContextHolder.removeContext();
            }

            while (TaskScopeContextHolder.contextExists()) {
                notifier.fireTestFailure(
                        new Failure(Description.createTestDescription(getTestClass().getJavaClass(), method.getName()),
                                new RuntimeException("Context stills exists?")));
                TaskScopeContextHolder.removeContext();
            }
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
