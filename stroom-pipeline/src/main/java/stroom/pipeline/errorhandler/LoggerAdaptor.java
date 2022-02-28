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

package stroom.pipeline.errorhandler;

import stroom.pipeline.LocationFactory;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerAdaptor extends net.sf.saxon.lib.StandardLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerAdaptor.class);

    private final String elementId;
    private final LocationFactory locationFactory;
    private final ErrorReceiver errorReceiver;

    public LoggerAdaptor(final String elementId,
                         final LocationFactory locationFactory,
                         final ErrorReceiver errorReceiver) {
        this.elementId = elementId;
        this.locationFactory = locationFactory;
        this.errorReceiver = errorReceiver;
    }

    @Override
    public void println(final String message, final int severity) {
        super.println(message, severity);

        LOGGER.debug("Logging an XSLT error.", message);

        Location location = null;

        Severity mappedSeverity = Severity.INFO;
        switch (severity) {
            case INFO -> mappedSeverity = Severity.INFO;
            case WARNING -> mappedSeverity = Severity.WARNING;
            case ERROR -> mappedSeverity = Severity.ERROR;
            case DISASTER -> mappedSeverity = Severity.FATAL_ERROR;
        }

        if (location == null) {
            location = locationFactory.create();
        }

        errorReceiver.log(mappedSeverity, location, elementId, message, new RuntimeException(message));
    }
}
