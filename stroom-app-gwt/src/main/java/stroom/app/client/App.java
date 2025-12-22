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

package stroom.app.client;

import stroom.app.client.gin.AppGinjectorUser;
import stroom.dispatch.client.QuietTaskMonitorFactory;
import stroom.preferences.client.UserPreferencesManager;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.Location;
import com.gwtplatform.mvp.client.DelayedBindRegistry;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class App implements EntryPoint {

    public final AppGinjectorUser ginjector = GWT.create(AppGinjectorUser.class);

    /**
     * This is the entry point method.
     */
    public void onModuleLoad() {
        // This is required for Gwt-Platform proxy's generator.
        DelayedBindRegistry.bind(ginjector);

//        GWT.setUncaughtExceptionHandler(e -> {
//            GWT.log(e.getClass().getName());
//
//            final StringBuilder stringBuilder = new StringBuilder();
//            appendStackTraces(e, stringBuilder);
////            final String stack;
////            if (e.getCause() != null && e.getCause().getStackTrace() != null) {
////                stack = appendStackTraces(e.getCause().getStackTrace());
////            } else if (e.getStackTrace() != null) {
////                stack = appendStackTraces(e.getStackTrace());
////            } else {
////                stack = "";
////            }
//
//            Window.alert("ERROR: " + stringBuilder);
//        });

        final String path = Window.Location.getPath();
        GWT.log("path: " + path + ", queryString: " + Window.Location.getQueryString());
        if (path.startsWith("/signIn")) {
            final String error = Location.getParameter("error");
            if ("login_required".equals(error)) {
                ginjector.getLoginPresenter().get().forceReveal();
            } else {
                ginjector.getAuthenticationErrorPresenter().get().forceReveal();
            }
        } else {
            final UserPreferencesManager userPreferencesManager = ginjector.getPreferencesManager();
            userPreferencesManager.fetch(preferences -> {
                userPreferencesManager.setCurrentPreferences(preferences);

                // Show the application panel.
                ginjector.getCorePresenter().get().forceReveal();

                // Register all plugins that will respond to

                // Start the login manager. This will attempt to auto login with PKI and
                // will therefore start the rest of the application.
                ginjector.getLoginManager().fetchUserAndPermissions();

                // Remember how places were used in case we want to use URLs and history
                // at some point.
                // ginjector.getPlaceManager().revealCurrentPlace();
            }, new QuietTaskMonitorFactory());
        }
    }

    private void appendStackTraces(final Throwable e, final StringBuilder stringBuilder) {
        if (e.getStackTrace() != null) {
            //noinspection SizeReplaceableByIsEmpty // cos GWT
            if (stringBuilder.length() > 0) {
                stringBuilder.append("\n");
            }
            stringBuilder.append(e.getClass().getName())
                    .append(" - ")
                    .append(e.getMessage())
                    .append(":\n");

            final String stack = Arrays.stream(e.getStackTrace())
                    .map(StackTraceElement::toString)
                    .collect(Collectors.joining("\n"));
            stringBuilder.append(stack);

            final Throwable cause = e.getCause();
            if (cause != null) {
                appendStackTraces(cause, stringBuilder);
            }
        }
    }
}
