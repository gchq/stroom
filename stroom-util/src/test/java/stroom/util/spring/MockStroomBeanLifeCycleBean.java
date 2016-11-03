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

package stroom.util.spring;

import org.junit.Assert;

public class MockStroomBeanLifeCycleBean {
    boolean running = false;
    boolean hasRun = false;

    @StroomStartup
    public void start() {
        if (running) {
            Assert.fail("Called start twice");
        }
        if (hasRun) {
            Assert.fail("Called start twice");
        }
        hasRun = false;
        running = true;
    }

    @StroomShutdown
    public void stop() {
        if (!running) {
            Assert.fail("Stopped called and not running");
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }
}
