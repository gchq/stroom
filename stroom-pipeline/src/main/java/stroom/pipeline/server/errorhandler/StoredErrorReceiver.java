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

package stroom.pipeline.server.errorhandler;

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.SAXParseException;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

public class StoredErrorReceiver implements ErrorReceiver {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StoredErrorReceiver.class);

    private long totalErrors;
    private List<StoredError> list = new ArrayList<>();

    @Override
    public void log(final Severity severity, final Location location, final String elementId, final String message,
            final Throwable e) {
        // Get cause SAXParseException if there is one.
        final Throwable cause = getCause(e);

        String msg;
        if (cause != null && (message == null || (e.getMessage() != null && e.getMessage().equals(message)))) {
            msg = MessageUtil.getMessage(cause.getMessage(), cause);
        } else {
            msg = MessageUtil.getMessage(message, e);
        }

        final Location loc = resolveLocation(location, cause);

        totalErrors++;
        list.add(new StoredError(severity, loc, elementId, msg));

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(msg, e);
        }
    }

    private Location resolveLocation(final Location location, final Throwable t) {
        Location loc = location;
        if (t != null && t instanceof SAXParseException) {
            final SAXParseException saxParseException = (SAXParseException) t;
            loc = new DefaultLocation(saxParseException.getLineNumber(), saxParseException.getColumnNumber());
        }
        return loc;
    }

    private Throwable getCause(final Throwable e) {
        Throwable t = e;
        while (t != null && !(t instanceof SAXParseException)) {
            t = t.getCause();
        }

        if (t != null && t instanceof SAXParseException) {
            return t;
        }

        return e;
    }

    public void replay(final ErrorReceiver errorReceiver) {
        if (errorReceiver != null) {
            for (final StoredError se : list) {
                errorReceiver.log(se.getSeverity(), se.getLocation(), se.getElementId(), se.getMessage(), null);
            }
        }
    }

    public long getTotalErrors() {
        return totalErrors;
    }

    public List<StoredError> getList() {
        return list;
    }
}
