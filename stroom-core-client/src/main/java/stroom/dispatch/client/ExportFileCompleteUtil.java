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

import com.google.gwt.core.client.GWT;
import com.gwtplatform.mvp.client.PresenterWidget;
import stroom.alert.client.event.AlertEvent;
import stroom.core.client.LocationManager;
import stroom.util.shared.Message;
import stroom.util.shared.ResourceGeneration;
import stroom.widget.popup.client.event.EnablePopupEvent;
import stroom.widget.popup.client.event.HidePopupEvent;

public final class ExportFileCompleteUtil {
    private ExportFileCompleteUtil() {
        // Utility.
    }

    public static void onSuccess(final LocationManager locationManager, final PresenterWidget<?> parent, final ResourceGeneration result) {
        if (parent != null) {
            HidePopupEvent.fire(parent, parent, false, false);
        }

        final String message = getMessage(result);
        if (message != null) {
            AlertEvent.fireWarn(parent, message, () -> {
                // Change the browser location to download the zip file.
                download(locationManager, result);
            });

        } else {
            // Change the browser location to download the zip file.
            download(locationManager, result);
        }
    }

    public static void onFailure(final PresenterWidget<?> parent) {
        if (parent != null) {
            EnablePopupEvent.fire(parent, parent);
        }
    }

    private static String getMessage(final ResourceGeneration result) {
        if (result.getMessageList() == null || result.getMessageList().size() == 0) {
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

    private static void download(final LocationManager locationManager, final ResourceGeneration result) {
        // Change the browser location to download the zip
        // file.
        locationManager.replace(GWT.getHostPageBaseURL() + "resourcestore/" + result.getResourceKey().getName() + "?UUID="
                + result.getResourceKey().getKey());
    }
}
