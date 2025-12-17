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
import org.xml.sax.SAXParseException;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;

public class ErrorListenerAdaptor implements ErrorListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorListenerAdaptor.class);

    private final ElementId elementId;
    private final LocationFactory locationFactory;
    private final ErrorReceiver errorReceiver;

    public ErrorListenerAdaptor(final ElementId elementId,
                                final LocationFactory locationFactory,
                                final ErrorReceiver errorReceiver) {
        this.elementId = elementId;
        this.locationFactory = locationFactory;
        this.errorReceiver = errorReceiver;
    }

    @Override
    public void warning(final TransformerException exception) throws TransformerException {
        log(Severity.WARNING, exception);
    }

    @Override
    public void error(final TransformerException exception) throws TransformerException {
        log(Severity.ERROR, exception);
    }

    @Override
    public void fatalError(final TransformerException exception) throws TransformerException {
        log(Severity.FATAL_ERROR, exception);
    }

    private void log(final Severity severity, final TransformerException exception) {
        LOGGER.debug("Logging a TransformedException.", exception);

        Location location = null;

        // Try and get the location from the exceptions source locator.
        final SourceLocator locator = exception.getLocator();
        if (locator != null) {
            location = locationFactory.create(locator.getLineNumber(), locator.getColumnNumber());
        }

        // If the source locator was null then try and find a location from a
        // parent exception.
        while (location == null && exception.getException() != null
               && exception.getException() instanceof final SAXParseException parent) {
            if (parent.getLineNumber() != -1 || parent.getColumnNumber() != -1) {
                location = locationFactory.create(parent.getLineNumber(), parent.getColumnNumber());
            }
        }

        if (location == null) {
            location = locationFactory.create();
        }

        errorReceiver.log(severity, location, elementId, exception.getMessage(), exception);
    }
}
