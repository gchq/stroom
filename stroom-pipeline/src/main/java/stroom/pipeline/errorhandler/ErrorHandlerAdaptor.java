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

package stroom.pipeline.errorhandler;

import stroom.pipeline.LocationFactory;
import stroom.util.shared.ElementId;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class ErrorHandlerAdaptor implements ErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorHandlerAdaptor.class);

    private final ElementId elementId;
    private final LocationFactory locationFactory;
    private final ErrorReceiver errorReceiver;

    public ErrorHandlerAdaptor(final ElementId elementId,
                               final LocationFactory locationFactory,
                               final ErrorReceiver errorReceiver) {
        this.elementId = elementId;
        this.locationFactory = locationFactory;
        this.errorReceiver = errorReceiver;
    }

    @Override
    public void warning(final SAXParseException exception) throws SAXException {
        log(Severity.WARNING, exception);
    }

    @Override
    public void error(final SAXParseException exception) throws SAXException {
        log(Severity.ERROR, exception);
    }

    @Override
    public void fatalError(final SAXParseException exception) throws SAXException {
        log(Severity.FATAL_ERROR, exception);
    }

    protected void log(final Severity severity, final SAXParseException exception) {
        LOGGER.debug("Logging SAXParseException", exception);

        final Location location = locationFactory.create(exception.getLineNumber(), exception.getColumnNumber());
        errorReceiver.log(severity, location, elementId, exception.getMessage(), exception);
    }

    public void log(final Severity severity, final Locator locator, final String message, final Throwable t) {
        LOGGER.debug(message, t);

        final Location location = locationFactory.create(locator.getLineNumber(), locator.getColumnNumber());
        errorReceiver.log(severity, location, elementId, message, t);
    }

    public ErrorReceiver getErrorReceiver() {
        return errorReceiver;
    }
}
