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

package stroom.dispatch.client;

import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.LocationManager;
import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.Severity;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;

public final class ExportFileCompleteUtil {

    private ExportFileCompleteUtil() {
        // Utility.
    }

    public static void onSuccess(final LocationManager locationManager,
                                 final HasHandlers hasHandlers,
                                 final ResourceGeneration result) {
        if (result != null) {
            final String message = getMessage(result);
            if (message != null) {
                Severity severity = getSeverity(result);
                if (severity == null) {
                    severity = Severity.INFO;
                }

                final AlertCallback callback = () -> {
                    // Change the browser location to download the zip file.
                    download(locationManager, result);
                };

                switch (severity) {
                    case INFO -> AlertEvent.fireInfo(hasHandlers, "Export Complete", message, callback);
                    case WARNING -> AlertEvent.fireWarn(hasHandlers, "Export Complete", message, callback);
                    case ERROR, FATAL_ERROR -> AlertEvent
                            .fireError(hasHandlers, "Export Complete", message, callback);
                }

            } else {
                // Change the browser location to download the zip file.
                download(locationManager, result);
            }
        }
    }

    private static String getMessage(final ResourceGeneration result) {
        if (NullSafe.isEmptyCollection(result.getMessageList())) {
            return null;
        }

        final StringBuilder stringBuilder = new StringBuilder();
        for (final Message msg : result.getMessageList()) {
            stringBuilder.append(msg.getSeverity().getDisplayValue());
            stringBuilder.append(": ");
            stringBuilder.append(msg.getMessage());
            stringBuilder.append("\n");
        }
        return stringBuilder.toString();
    }

    private static Severity getSeverity(final ResourceGeneration result) {
        Severity severity = null;
        if (result.getMessageList() != null) {
            for (final Message msg : result.getMessageList()) {
                if (msg.getSeverity() != null) {
                    if (severity == null || severity.lessThan(msg.getSeverity())) {
                        severity = msg.getSeverity();
                    }
                }
            }
        }
        return severity;
    }

    private static void download(final LocationManager locationManager, final ResourceGeneration result) {
        // Change the browser location to download the zip file.
        // The name is needed so the browser has a default name for the download, but is not used
        // by the servlet
        locationManager.replace(GWT.getHostPageBaseURL() +
                                "resourcestore/" +
                                result.getResourceKey().getName() +
                                "?uuid=" +
                                result.getResourceKey().getKey());
    }
}
