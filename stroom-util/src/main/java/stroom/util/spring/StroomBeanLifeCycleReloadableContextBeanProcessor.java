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

package stroom.util.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.SmartLifecycle;
import stroom.util.concurrent.AtomicSequence;

import javax.annotation.Resource;
import java.util.ArrayDeque;
import java.util.HashMap;

/**
 * As we are re-loading beans when our properties change we want to make sure
 * start is only called when the old bean has stopped.
 */
public class StroomBeanLifeCycleReloadableContextBeanProcessor implements SmartLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomBeanLifeCycleReloadableContextBeanProcessor.class);

    private static final HashMap<String, ArrayDeque<StroomBeanLifeCycleReloadableContextBeanProcessor>> nameInstanceStack = new HashMap<>();
    private static AtomicSequence instanceCount = new AtomicSequence();

    private volatile String name;
    private volatile boolean startCalled = false;
    private volatile boolean stopCalled = false;
    private volatile boolean started = false;
    private volatile boolean stopped = false;
    private volatile int instance;

    @Resource
    private volatile StroomBeanLifeCycle stroomBeanLifeCycle;

    public synchronized void setStroomBeanLifeCycle(final StroomBeanLifeCycle stroomBeanLifeCycle) {
        this.stroomBeanLifeCycle = stroomBeanLifeCycle;
        this.instance = instanceCount.next();
    }

    private synchronized void doStop() {
        doLog("STOPPING");

        if (!started) {
            LOGGER.error("Stopped Called When Not Started");
            return;
        }
        if (stopped) {
            LOGGER.error("Stopped Called Twice");
            return;
        }
        stopped = true;

        StroomBeanMethodExecutable work;
        while ((work = stroomBeanLifeCycle.getStopExecutable()) != null) {
            doLog("STOPPING ->", work.toString());
            work.exec(new DummyTask());
            doLog("STOPPED  ->", work.toString());

        }

        doLog("STOP COMPLETE");
    }

    private synchronized void doStart() {
        doLog("STARTING");

        if (started) {
            throw new IllegalStateException("Start Called Twice");
        }
        started = true;

        StroomBeanMethodExecutable work;
        while ((work = stroomBeanLifeCycle.getStartExecutable()) != null) {
            doLog("STARTING ->", work.toString());
            work.exec(new DummyTask());
            doLog("STARTED  ->", work.toString());
        }

        doLog("START COMPLETE");
    }

    private void doLog(final String msg) {
        LOGGER.info("** " + name + " " + instance + " " + msg + " **");
    }

    private void doLog(final String msg, final String beanName) {
        LOGGER.info("** " + name + " " + instance + " " + msg + " " + beanName + " **");
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("name=");
        builder.append(name);
        builder.append(",instance=");
        builder.append(instance);
        builder.append(",started=");
        builder.append(started);
        builder.append(",stopped=");
        builder.append(stopped);
        return builder.toString();
    }

    @Required
    public synchronized void setName(final String name) {
        final ArrayDeque<StroomBeanLifeCycleReloadableContextBeanProcessor> stack = nameInstanceStack.get(name);
        if (stack == null) {
            nameInstanceStack.put(name, new ArrayDeque<>());
        }
        this.name = name;
    }

    public boolean isStarted() {
        return started;
    }

    /**
     * FMK hook
     */
    @Override
    public synchronized void start() {
        // Avoid multiple FMK Calls
        if (startCalled) {
            return;
        }
        startCalled = true;
        synchronized (nameInstanceStack) {
            final ArrayDeque<StroomBeanLifeCycleReloadableContextBeanProcessor> stack = nameInstanceStack.get(name);

            // Nothing running ... OK to start
            if (stack.size() == 0) {
                doStart();
            }
            stack.add(this);

        }
    }

    /**
     * FMK hook
     */
    @Override
    public void stop() {
        // Avoid multiple FMK Calls
        if (stopCalled) {
            return;
        }
        stopCalled = true;
        synchronized (nameInstanceStack) {
            doStop();

            final ArrayDeque<StroomBeanLifeCycleReloadableContextBeanProcessor> stack = nameInstanceStack.get(name);

            if (!stack.remove(this)) {
                throw new IllegalStateException("instance not in our stack??");
            }

            if (stack.size() > 0) {
                final StroomBeanLifeCycleReloadableContextBeanProcessor head = stack.peek();
                if (!head.isStarted()) {
                    head.doStart();
                }
            }

        }

    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(final Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public boolean isRunning() {
        return started && !stopped;
    }
}
