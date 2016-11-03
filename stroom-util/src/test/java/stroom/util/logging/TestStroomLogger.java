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

package stroom.util.logging;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.test.StroomExpectedException;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.thread.ThreadUtil;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStroomLogger {
    @Test
    public void testSimple() {
        final StroomLogger stroomLogger = StroomLogger.getLogger(TestStroomLogger.class);
        stroomLogger.debug("testSimple() %s", "start");
        Assert.assertEquals("test one", stroomLogger.buildMessage("test %s", "one", "two"));
        Assert.assertEquals("test one two", stroomLogger.buildMessage("test %s %s", "one", "two"));
        Assert.assertEquals("test one two", stroomLogger.buildMessage("test %s %s", "one", "two", new Throwable()));
        Assert.assertNotNull(stroomLogger.extractThrowable("test %s %s", "one", "two", new Throwable()));
        stroomLogger.debug("testSimple() %s", "stop");
    }

    @Test
    public void testNumbers() {
        final StroomLogger stroomLogger = StroomLogger.getLogger(TestStroomLogger.class);
        Assert.assertEquals("1/2", stroomLogger.buildMessage("%s/%s", 1, 2));
    }

    @Test
    public void testLogExecutionTime() {
        final LogExecutionTime logExecutionTime = new LogExecutionTime();
        final StroomLogger stroomLogger = StroomLogger.getLogger(TestStroomLogger.class);
        Assert.assertTrue(stroomLogger.buildMessage("%s", logExecutionTime).contains("ms"));
    }

    @Test
    public void testInterval() {
        final StroomLogger stroomLogger = StroomLogger.getLogger(TestStroomLogger.class);
        stroomLogger.setInterval(1);
        Assert.assertTrue(stroomLogger.checkInterval());
        Assert.assertTrue(ThreadUtil.sleep(100));
        Assert.assertTrue(stroomLogger.checkInterval());
    }

    private String produceMessage(final String level) {
        return "this is my big " + level + " msg";
    }

    @Test
    @StroomExpectedException(exception = Throwable.class)
    public void testMessageSupplier() {
        final StroomLogger stroomLogger = StroomLogger.getLogger(TestStroomLogger.class);

        stroomLogger.trace(() -> produceMessage("trace"));
        stroomLogger.trace(() -> produceMessage("trace"), new Throwable());
        stroomLogger.debug(() -> produceMessage("debug"));
        stroomLogger.debug(() -> produceMessage("debug"), new Throwable());
        stroomLogger.warn(() -> produceMessage("warn"));
        stroomLogger.warn(() -> produceMessage("warn"), new Throwable());
        stroomLogger.error(() -> produceMessage("error"));
        stroomLogger.error(() -> produceMessage("error"), new Throwable());
        stroomLogger.fatal(() -> produceMessage("fatal"));
        stroomLogger.fatal(() -> produceMessage("fatal"), new Throwable());

    }

    @Test
    public void testIfTraceIsEnabled() {
        final StroomLogger stroomLogger = StroomLogger.getLogger(TestStroomLogger.class);

        final AtomicInteger counter = new AtomicInteger(0);

        stroomLogger.ifTraceIsEnabled(() -> counter.incrementAndGet());

        Assert.assertEquals(stroomLogger.isTraceEnabled() ? 1 : 0, counter.get());
    }

    @Test
    public void testIfDebugIsEnabled() {
        final StroomLogger stroomLogger = StroomLogger.getLogger(TestStroomLogger.class);

        final AtomicInteger counter = new AtomicInteger(0);

        stroomLogger.ifDebugIsEnabled(() -> counter.incrementAndGet());

        Assert.assertEquals(stroomLogger.isDebugEnabled() ? 1 : 0, counter.get());
    }

}
