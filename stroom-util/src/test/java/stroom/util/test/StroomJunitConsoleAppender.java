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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;

public class StroomJunitConsoleAppender extends ConsoleAppender {
    private static Set<Class<? extends Throwable>> expectedExceptionSet;
    private static List<Throwable> unexpectedExceptions = new ArrayList<Throwable>();

    public static void setExpectedException(Class<? extends Throwable>[] expectedException) {
        expectedExceptionSet = new HashSet<>();
        unexpectedExceptions = new ArrayList<>();
        if (expectedException != null) {
            for (Class<? extends Throwable> ex : expectedException) {
                expectedExceptionSet.add(ex);
            }
        }
    }

    public static List<Throwable> getUnexpectedExceptions() {
        return unexpectedExceptions;
    }

    @Override
    public synchronized void doAppend(LoggingEvent event) {
        if (event.getThrowableInformation() != null) {
            Throwable throwable = event.getThrowableInformation().getThrowable();
            if (expectedExceptionSet == null || !expectedExceptionSet.contains(throwable.getClass())) {
                unexpectedExceptions.add(throwable);
            } else {
                // Ignore the expected exception
                event = new LoggingEvent(event.getFQNOfLoggerClass(), event.getLogger(), event.getTimeStamp(),
                        Level.DEBUG, "Ignore Exception - " + throwable.getMessage() + " - " + event.getMessage(), null);
            }
        }
        super.doAppend(event);
    }

}
