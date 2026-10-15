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

package stroom.gwt.workbench.framework.client.story;

/// Builds and parses the URLs used by the workbench, which are the same as React Storybook's:
///
/// * Manager: `/?path=/story/<story id>`
/// * Settings pages: `/?path=/settings/<page>`, e.g. `about` or `shortcuts`
/// * Preview: `/iframe.html?id=<story id>&viewMode=story`, with `&globals=theme:light` for a theme
///   other than the default
public final class StoryUrls {

    /// The name of the manager's query parameter that holds the path.
    public static final String PATH_PARAM = "path";
    /// The name of the preview's query parameter that holds the story id.
    public static final String ID_PARAM = "id";
    /// The name of the query parameter that holds the args the user has changed, as encoded by
    /// [stroom.gwt.workbench.framework.client.args.ArgsCodec].
    public static final String ARGS_PARAM = "args";
    /// The name of the preview's query parameter that holds the globals, e.g. `theme:light`.
    public static final String GLOBALS_PARAM = "globals";

    private static final String THEME_GLOBAL = "theme";

    private static final String STORY_PATH_PREFIX = "/story/";
    private static final String SETTINGS_PATH_PREFIX = "/settings/";
    private static final String PREVIEW_PAGE = "iframe.html";

    private StoryUrls() {
        // Static utility
    }

    /// @param storyId The id of a story.
    /// @return The manager path for the story, e.g. `/story/widgets-buttons-button--default`.
    public static String managerPath(final String storyId) {
        return STORY_PATH_PREFIX + storyId;
    }

    /// @param storyId The id of a story.
    /// @return The relative manager URL for the story, e.g. `?path=/story/widgets-buttons-button--default`.
    public static String managerUrl(final String storyId) {
        return "?" + PATH_PARAM + "=" + managerPath(storyId);
    }

    /// @param storyId The id of a story.
    /// @return The relative preview URL for the story, e.g.
    /// `iframe.html?id=widgets-buttons-button--default&viewMode=story`.
    public static String previewUrl(final String storyId) {
        return previewUrl(storyId, null);
    }

    /// @param storyId     The id of a story.
    /// @param encodedArgs The args the user has changed, may be null or empty.
    /// @return The relative preview URL for the story with the args.
    public static String previewUrl(final String storyId, final String encodedArgs) {
        return previewUrl(storyId, encodedArgs, StoryTheme.DEFAULT);
    }

    /// @param storyId     The id of a story.
    /// @param encodedArgs The args the user has changed, may be null or empty.
    /// @param theme       The theme to show it in; the default (or null) adds no globals.
    /// @return The relative preview URL for the story with the args and theme, e.g.
    /// `iframe.html?id=widgets-buttons-button--default&viewMode=story&globals=theme:light`.
    public static String previewUrl(final String storyId, final String encodedArgs, final StoryTheme theme) {
        return PREVIEW_PAGE + "?" + ID_PARAM + "=" + storyId + "&viewMode=story" + argsParam(encodedArgs)
               + (theme == null || theme == StoryTheme.DEFAULT
                ? ""
                : "&" + GLOBALS_PARAM + "=" + THEME_GLOBAL + ":" + theme.getId());
    }

    /// @param globals The value of the preview's [#GLOBALS_PARAM], e.g. `theme:light`, as
    ///                `name:value` pairs separated by `;`; may be null.
    /// @return The theme it gives, or [StoryTheme#DEFAULT] if it gives none.
    public static StoryTheme themeFromGlobals(final String globals) {
        if (globals != null) {
            for (final String global : globals.split(";")) {
                final int colon = global.indexOf(':');
                if (colon > 0 && THEME_GLOBAL.equals(global.substring(0, colon).trim())) {
                    return StoryTheme.fromId(global.substring(colon + 1).trim());
                }
            }
        }
        return StoryTheme.DEFAULT;
    }

    /// @param storyId     The id of a story.
    /// @param encodedArgs The args the user has changed, may be null or empty.
    /// @return The relative manager URL for the story with the args, e.g.
    /// `?path=/story/widgets-buttons-button--default&args=loading:!true`.
    public static String managerUrl(final String storyId, final String encodedArgs) {
        return managerUrl(storyId) + argsParam(encodedArgs);
    }

    private static String argsParam(final String encodedArgs) {
        if (encodedArgs == null || encodedArgs.isEmpty()) {
            return "";
        }
        // The args use %XX escapes of their own, which must survive the URL being decoded
        return "&" + ARGS_PARAM + "=" + encodedArgs.replace("%", "%25").replace(" ", "+");
    }

    /// @param path The value of the manager's `path` query parameter.
    /// @return The story id in the path, or null if the path is not a story path.
    public static String storyIdFromPath(final String path) {
        if (path == null || !path.startsWith(STORY_PATH_PREFIX)) {
            return null;
        }
        final String id = path.substring(STORY_PATH_PREFIX.length()).trim();
        return id.isEmpty()
                ? null
                : id;
    }

    /// @param page The name of a settings page, e.g. `about`.
    /// @return The relative manager URL for the page, e.g. `?path=/settings/about`.
    public static String settingsUrl(final String page) {
        return "?" + PATH_PARAM + "=" + SETTINGS_PATH_PREFIX + page;
    }

    /// @param path The value of the manager's `path` query parameter.
    /// @return The settings page in the path, e.g. `about`, or null if the path is not a settings
    /// path.
    public static String settingsPageFromPath(final String path) {
        if (path == null || !path.startsWith(SETTINGS_PATH_PREFIX)) {
            return null;
        }
        final String page = path.substring(SETTINGS_PATH_PREFIX.length()).trim();
        return page.isEmpty()
                ? null
                : page;
    }

    /// @param pagePath The path of the current page, e.g. `/iframe.html`.
    /// @return True if the page is the preview page rather than the manager.
    public static boolean isPreviewPage(final String pagePath) {
        return pagePath != null && pagePath.endsWith("/" + PREVIEW_PAGE);
    }
}
