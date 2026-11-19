/*
 * Copyright 2023 Crown Copyright
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

package stroom.query.client.component;

import stroom.activity.client.CurrentActivity;
import stroom.activity.shared.Activity;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.QueryConfig;
import stroom.widget.component.client.AppRoot;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.inject.Provider;

import javax.inject.Inject;

/// The info a query is run with: asks for it ([QueryInfoPromptController]) before a query is run,
/// when the site asks for it and the current activity doesn't say it isn't needed, and keeps it.
public class QueryInfo {

    private final CurrentActivity currentActivity;
    private final UiConfigCache uiConfigCache;
    private final Provider<QueryInfoPromptController> queryInfoPromptProvider;
    private final AppRoot appRoot;
    private String message;

    /// Made by GIN, as one for the app.
    ///
    /// @param currentActivity         Says whether the current activity needs query info.
    /// @param uiConfigCache           Says whether the site asks for query info, and how.
    /// @param queryInfoPromptProvider Makes the dialog that asks for it.
    /// @param appRoot                 Owns the dialog while it is shown.
    @Inject
    public QueryInfo(final CurrentActivity currentActivity,
                     final UiConfigCache uiConfigCache,
                     final Provider<QueryInfoPromptController> queryInfoPromptProvider,
                     final AppRoot appRoot) {
        this.currentActivity = currentActivity;
        this.uiConfigCache = uiConfigCache;
        this.queryInfoPromptProvider = queryInfoPromptProvider;
        this.appRoot = appRoot;
    }

    /// Asks for the query info, if it is needed, then runs the query; Cancel doesn't run it.
    ///
    /// @param runnable           Runs the query.
    /// @param taskMonitorFactory Shows the progress of the activity's and config's lookups.
    public void prompt(final Runnable runnable, final TaskMonitorFactory taskMonitorFactory) {
        currentActivity.getActivity(activity -> {
            final boolean required = isRequired(activity);
            uiConfigCache.get(uiConfig -> {
                if (uiConfig != null) {
                    final QueryConfig queryConfig = uiConfig.getQuery();
                    if (queryConfig.getInfoPopup().isEnabled() && required) {
                        // The app owns the dialog while it is shown
                        queryInfoPromptProvider.get().show(appRoot,
                                queryConfig.getInfoPopup().getTitle(),
                                queryConfig.getInfoPopup().getValidationRegex(),
                                message,
                                queryInfo -> {
                                    message = queryInfo;
                                    runnable.run();
                                });
                    } else {
                        message = null;
                        runnable.run();
                    }
                }
            }, taskMonitorFactory);
        }, taskMonitorFactory);
    }

    /// @return The query info last given, or null if none was asked for.
    public String getMessage() {
        return message;
    }

    /// Whether an activity needs query info: unless its `requireQueryInfo` property is "false",
    /// ignoring case.
    ///
    /// @param activity The current activity, or null for none.
    /// @return True if query info is needed.
    public static boolean isRequired(final Activity activity) {
        if (activity != null && activity.getDetails() != null) {
            final String value = activity.getDetails().value("requireQueryInfo");
            return value == null || !value.equalsIgnoreCase("false");
        }
        return true;
    }
}
