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

package stroom.util.test;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogExecutionTime;
import stroom.util.task.ExternalShutdownController;
import stroom.util.thread.ThreadScopeContextHolder;

import java.io.File;

public class StroomJUnit4ClassRunner extends BlockJUnit4ClassRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomJUnit4ClassRunner.class);

    public StroomJUnit4ClassRunner(final Class<?> clazz) throws InitializationError {
        super(clazz);
    }

    @Override
    public void run(final RunNotifier notifier) {
        try {
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
                ThreadScopeContextHolder.createContext();

                final LogExecutionTime logExecutionTime = new LogExecutionTime();
                try {
                    runChildBefore(this, method, notifier);
                    super.runChild(method, notifier);

                } finally {
                    runChildAfter(this, method, notifier, logExecutionTime);
                }

            } finally {
                ThreadScopeContextHolder.destroyContext();
            }
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        ExternalShutdownController.shutdown();
    }

    static void runChildBefore(final BlockJUnit4ClassRunner runner, final FrameworkMethod method,
            final RunNotifier notifier) {
        final StroomExpectedException stroomExpectedException = method.getAnnotation(StroomExpectedException.class);
        if (stroomExpectedException != null) {
//            StroomJunitConsoleAppender.setExpectedException(stroomExpectedException.exception());
            LOGGER.info(">>> {}.  Expecting Exceptions {}", method.getMethod(), stroomExpectedException.exception());
        } else {
            LOGGER.info(">>> {}", method.getMethod());
        }

    }

    static void runChildAfter(final BlockJUnit4ClassRunner runner, final FrameworkMethod method,
            final RunNotifier notifier, final LogExecutionTime logExecutionTime) {
        LOGGER.info("<<< {} took {}", method.getMethod(), logExecutionTime);
    }
}
