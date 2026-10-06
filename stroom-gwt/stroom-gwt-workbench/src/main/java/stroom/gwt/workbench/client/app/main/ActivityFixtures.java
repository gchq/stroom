/*
 * Copyright 2026 Crown Copyright
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


package stroom.gwt.workbench.client.app.main;

import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.rest.StartupFixtures;

import com.google.gwt.regexp.shared.RegExp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// The fake activity service (`ActivityResource`, `/activity/v1`) and activity UI config shared by
/// the `App/Main/ManageActivityDialog` and `App/Main/Activity Chooser` stories.
final class ActivityFixtures {

    /// The default `ActivityConfig.editorBody` template: a code and a description with a length
    /// rule.
    static final String EDITOR_BODY = "Activity Code:<br/>\n"
            + "<input type=\"text\" name=\"code\"></input><br/><br/>\n"
            + "Activity Description:<br/>\n"
            + "<textarea rows=\"4\" style=\"width:100%;height:80px\" name=\"description\"\n"
            + "  validation=\".{10,}\"\n"
            + "  validationMessage=\"The activity description must be at least 10 characters long.\">\n"
            + "</textarea>";

    static final String PATH = "/activity/v1";
    static final String CURRENT_PATH = PATH + "/current";

    private ActivityFixtures() {
        // Static utility
    }

    /// @param managerTitle The activity manager's (chooser's) title.
    /// @return The UI config with activities enabled and the default editor template.
    static String uiConfig(final String managerTitle) {
        final String activity = "\"activity\": {\"chooseOnStartup\": false, \"enabled\": true, "
                + "\"editorTitle\": \"Edit Activity\", \"managerTitle\": \"" + managerTitle + "\", "
                + "\"editorBody\": " + quote(EDITOR_BODY) + "}";
        final String defaults = StartupFixtures.DEFAULT_UI_CONFIG;
        final int start = defaults.indexOf("\"activity\":");
        final int end = defaults.indexOf('}', start) + 1;
        return defaults.substring(0, start) + activity + defaults.substring(end);
    }

    /// @param id         The activity's id.
    /// @param properties The activity's properties, as JSON objects.
    /// @return An activity, as JSON.
    static String activity(final int id, final String... properties) {
        return "{\"id\": " + id + ", \"details\": {\"properties\": [" + String.join(", ", properties) + "]}}";
    }

    /// @param id              The property's id.
    /// @param name            The property's name.
    /// @param value           The property's value.
    /// @param showInSelection Whether the property is shown in the current activity's summary.
    /// @return A property of an activity, as JSON, shown in the list.
    static String prop(final String id, final String name, final String value, final boolean showInSelection) {
        return "{\"id\": \"" + id + "\", \"name\": \"" + name + "\", \"value\": \"" + value
                + "\", \"showInList\": true, \"showInSelection\": " + showInSelection + "}";
    }

    /// @param activities The activities, as JSON.
    /// @return A page of activities, as `ActivityResource.list` returns.
    static String page(final String... activities) {
        return "{\"values\": [" + String.join(", ", activities) + "], \"pageResponse\": {\"offset\": 0, "
                + "\"length\": " + activities.length + ", \"total\": " + activities.length + ", \"exact\": true}}";
    }

    /// Adds the routes that every activity story needs: the current activity, the quick filter's
    /// field definitions, validation (as the server does it, with each property's regex), setting
    /// the current activity and updating an activity (both echoes).
    ///
    /// @param builder The fixtures to add to.
    /// @param current The current activity, as JSON, or `null`.
    /// @return The builder.
    static RestFixtures.Builder common(final RestFixtures.Builder builder, final String current) {
        return builder
                .get(CURRENT_PATH, current == null
                        ? RestReply.noContent()
                        : RestReply.json(current))
                .put(CURRENT_PATH, ActivityFixtures::echo)
                .get(PATH + "/fields", RestReply.json("[]"))
                .post(PATH + "/validate", ActivityFixtures::validate)
                .route(RequestMatcher.put(PATH + "/*"), ActivityFixtures::echo);
    }

    private static String quote(final String text) {
        final StringBuilder sb = new StringBuilder("\"");
        for (final char c : text.toCharArray()) {
            if (c == '"' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static RestReply echo(final RecordedRequest request) {
        return request.getBody() == null || request.getBody().isEmpty()
                ? RestReply.noContent()
                : RestReply.json(request.getBody());
    }

    // As the server does: each property with a validation regex must match it in full
    private static RestReply validate(final RecordedRequest request) {
        final Map<?, ?> activity = (Map<?, ?>) JsonValues.parse(request.getBody());
        final Map<?, ?> details = (Map<?, ?>) activity.get("details");
        final List<String> messages = new ArrayList<>();
        if (details != null && details.get("properties") instanceof List) {
            for (final Object item : (List<?>) details.get("properties")) {
                final Map<?, ?> prop = (Map<?, ?>) item;
                final Object validation = prop.get("validation");
                final Object value = prop.get("value");
                if (validation != null && value != null
                        && !RegExp.compile("^(?:" + validation + ")$").test(value.toString())) {
                    final Object message = prop.get("validationMessage");
                    messages.add(message != null
                            ? message.toString()
                            : prop.get("name") + " is invalid");
                }
            }
        }
        return RestReply.json("{\"valid\": " + messages.isEmpty() + ", \"messages\": "
                + quote(String.join("\n", messages)) + "}");
    }
}
