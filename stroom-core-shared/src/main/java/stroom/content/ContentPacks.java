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

package stroom.content;

public class ContentPacks {

    private static final GitRepo STROOM_CONTENT_GIT_REPO =
            new GitRepo("stroom-content",
                    "https://github.com/gchq/stroom-content.git",
                    "7.10",
                    "0c6da31bc3d1d2d87be3d681c859b9a015be4afb");
    private static final GitRepo STROOM_VISUALISATION_DEV_GIT_REPO =
            new GitRepo("stroom-visualisations-dev",
                    "https://github.com/gchq/stroom-visualisations-dev.git",
                    "main",
                    "5d650d01bd2839e5f89e9f897d2f0e7a733690f2");

    public static final ContentPack CORE_XML_SCHEMAS_PACK = createStandardContentPack("core-xml-schemas");

    public static final ContentPack EVENT_LOGGING_XML_SCHEMA_PACK =
            createStandardContentPack("event-logging-xml-schema");

    public static final ContentPack STANDARD_PIPELINES_PACK = createStandardContentPack("standard-pipelines");

    public static final ContentPack TEMPLATE_PIPELINES_PACK = createStandardContentPack("template-pipelines");

    public static final ContentPack PLANB = createStandardContentPack("planb");

    public static final ContentPack VISUALISATIONS = createVisualisationContentPack(
            "visualisations-production");

    private static ContentPack createStandardContentPack(final String name) {
        return new ContentPack(name, "source/" + name + "/stroomContent", STROOM_CONTENT_GIT_REPO);
    }

    private static ContentPack createVisualisationContentPack(final String name) {
        return new ContentPack(name, "war/stroom_content", STROOM_VISUALISATION_DEV_GIT_REPO);
    }

    private ContentPacks() {
    }
}
