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

package stroom.app.docs;

import stroom.test.common.docs.StroomDocsUtil;
import stroom.test.common.docs.StroomDocsUtil.GeneratesDocumentation;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ScanResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenerateSnippetsDoc implements DocumentationGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GenerateSnippetsDoc.class);

    private static final Pattern WHOLE_LINE_COMMENT_PATTERN = Pattern.compile(
            "^\\s*//.*$\\n?", Pattern.MULTILINE);
    private static final Pattern END_OF_LINE_COMMENT_PATTERN = Pattern.compile(
            "\\s*//.*$", Pattern.MULTILINE);
    private static final Pattern CONTENT_VALUE_PATTERN = Pattern.compile(
            "`([^`]*)`\\.trim\\(\\).*", Pattern.MULTILINE);
    private static final Pattern LEADING_TRAILING_NEW_LINE_PATTERN = Pattern.compile(
            "(^\\n|\\n$)");
    private static final Pattern ARRAY_TRAILING_COMMA_PATTERN = Pattern.compile("},\\s*]",
            Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern OBJECT_TRAILING_COMMA_PATTERN = Pattern.compile(",\\s*}(?=[,\\]])",
            Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern SNIPPETS_ARRAY_PATTERN = Pattern.compile(
            "(?<=exports\\.snippets = )\\[.*](?=;)", Pattern.DOTALL);

    private static final Path REPO_ROOT_PATH = Paths.get(".")
            .toAbsolutePath()
            .normalize();

    private static final Path SNIPPETS_DIR_PATH = REPO_ROOT_PATH.resolve(
                    "stroom-core-client-widget/src/main/java/edu/ycp/cs/dh/acegwt/public/ace/snippets")
            .toAbsolutePath()
            .normalize();

    private static final Path SNIPPETS_REFERENCE_FILE_SUB_PATH = Paths.get(
            "content/en/docs/reference-section/snippet-reference.md");

    private static final Path XML_SNIPPETS_FILE = SNIPPETS_DIR_PATH.resolve("xml.js");
    private static final Path DATA_SPLITTER_SNIPPETS_FILE = SNIPPETS_DIR_PATH.resolve("stroom_data_splitter.js");
    private static final Path FRAGMENT_PARSER_SNIPPETS_FILE = SNIPPETS_DIR_PATH.resolve(
            "stroom_fragment_parser.js");
    private static final Path MARKDOWN_SNIPPETS_FILE = SNIPPETS_DIR_PATH.resolve("markdown.js");
    private static final Path STROOM_SNIPPETS_FILE = SNIPPETS_DIR_PATH.resolve("stroom_query.js");

    @SuppressWarnings("checkstyle:LineLength")
    private static final List<SnippetDefinition> SNIPPETS_FILES = List.of(
            new SnippetDefinition(
                    "XML/XSLT",
                    XML_SNIPPETS_FILE,
                    "",
                    "xml"),
            new SnippetDefinition(
                    "Data Splitter",
                    DATA_SPLITTER_SNIPPETS_FILE,
                    "",
                    "xml"),
            new SnippetDefinition(
                    "XMLFragmentParser",
                    FRAGMENT_PARSER_SNIPPETS_FILE,
                    "",
                    "xml"),
            new SnippetDefinition(
                    "Documentation (Markdown)",
                    MARKDOWN_SNIPPETS_FILE,
                    "",
                    "markdown"),
            new SnippetDefinition(
                    "Stroom Query Language",
                    STROOM_SNIPPETS_FILE,
                    """

                            All [Expression Functions]({{< relref "docs/reference-section/expressions" >}}) are available as snippets.
                            They do not currently have `tab` triggers.
                            """,
                    "text")
    );

    @SuppressWarnings("checkstyle:LineLength")
    private static final String FOOTER = """

            ## Dashboard Table Expression Editor Snippets

            All [Expression Functions]({{< relref "docs/reference-section/expressions" >}}) are available as snippets.
            They do not currently have `tab` triggers.


            """; // One of the new lines seems to get lost when written to file

    private final ObjectMapper objectMapper;

    @GeneratesDocumentation
    public static void main(final String[] args) {
        new GenerateSnippetsDoc().generateDocs();
    }

    @Override
    public void generateAll(final ScanResult scanResult) {
        generateDocs();
    }

    public GenerateSnippetsDoc() {
        this.objectMapper = new ObjectMapper();
    }

    private void generateDocs() {

        LOGGER.debug("path: {}", SNIPPETS_DIR_PATH);

        if (!Files.isDirectory(SNIPPETS_DIR_PATH)) {
            throw new RuntimeException(LogUtil.message("Ace snippets dir '{}' does not exist.",
                    SNIPPETS_DIR_PATH.toAbsolutePath().normalize()));
        }

        final StringBuilder stringBuilder = new StringBuilder();
        for (final SnippetDefinition definition : SNIPPETS_FILES) {
            final List<Snippet> snippets = parseFile(definition.filePath);

            appendSnippets(stringBuilder,
                    definition.name,
                    definition.description,
                    definition.fencedBlockSyntaxType,
                    snippets);
        }

        if (!NullSafe.isBlankString(FOOTER)) {
            stringBuilder.append(FOOTER);
        }

        final String generatedContent = stringBuilder.toString();

        final Path file = StroomDocsUtil.resolveStroomDocsFile(SNIPPETS_REFERENCE_FILE_SUB_PATH);

        final boolean didReplace = StroomDocsUtil.replaceGeneratedContent(file, generatedContent);

        if (didReplace) {
            LOGGER.info("Replaced generated content in file: {}", file);
        } else {
            LOGGER.info("No change made to file: {}", file);
        }
    }

    private void appendSnippets(final StringBuilder sb,
                                final String name,
                                final String description,
                                final String fencedBlockSyntaxType,
                                final List<Snippet> snippets) {

        sb.append("## ")
                .append(name)
                .append(" Snippets");

        if (!NullSafe.isBlankString(description)) {
            sb.append(description);
        }

        for (final Snippet snippet : snippets) {

            // if content contains a fenced block, e.g. markdown then we have to
            // user 4 back ticks for our outer fenced block, so they don't conflict
            final String fenceStartEnd = snippet.content.contains("```")
                    ? "````"
                    : "```";

            final String snippetSection = LogUtil.message("""


                            ### {} (`{}`)

                            **Name**: `{}`, **Tab Trigger**: `{}`

                            {}{}
                            {}
                            {}`
                            """,
                    snippet.name,
                    snippet.tabTrigger,
                    snippet.name,
                    snippet.tabTrigger,
                    fenceStartEnd,
                    Objects.requireNonNullElse(fencedBlockSyntaxType, ""),
                    snippet.content,
                    fenceStartEnd);

            sb.append(snippetSection);
        }
    }

    private List<Snippet> parseFile(final Path snippetFile) {

        if (!Files.isRegularFile(snippetFile)) {
            throw new RuntimeException(LogUtil.message("Snippet file {} does not exist", snippetFile));
        }

        final String jsStr;
        try {
            jsStr = Files.readString(snippetFile);
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error reading snippet file {}: {}",
                    snippetFile, LogUtil.exceptionMessage(e)), e);
        }

        if (NullSafe.isBlankString(jsStr)) {
            throw new RuntimeException(LogUtil.message("Snippet file {} has no content", snippetFile));
        }

        try {

            final String jsonStr;
            final Matcher matcher = SNIPPETS_ARRAY_PATTERN.matcher(jsStr);
            if (matcher.find()) {
                jsonStr = matcher.group(0);
            } else {
                throw new RuntimeException(LogUtil.message(
                        "Error extracting snippets JSON from file {} using pattern '{}'",
                        snippetFile, SNIPPETS_ARRAY_PATTERN.pattern()));
            }

            // Remove all comment lines, hopefully no snippets have java style comments in
            String jsonStr2 = jsonStr.trim();
            jsonStr2 = WHOLE_LINE_COMMENT_PATTERN.matcher(jsonStr).replaceAll("");
//            jsonStr2 = END_OF_LINE_COMMENT_PATTERN.matcher(jsonStr2).replaceAll("");
            // Remove trailing comma from array
            jsonStr2 = ARRAY_TRAILING_COMMA_PATTERN.matcher(jsonStr2).replaceAll("}]");
            // Remove trailing comma from last object property
            jsonStr2 = OBJECT_TRAILING_COMMA_PATTERN.matcher(jsonStr2).replaceAll("}");

            if (Objects.equals(jsStr, jsonStr2)) {
                LOGGER.warn("No comments replaced");
            }

            String jsonStr3 = jsonStr2;
            final Matcher contentValueMatcher = CONTENT_VALUE_PATTERN.matcher(jsonStr2);
            while (contentValueMatcher.find()) {
                final String originalContentValue = contentValueMatcher.group(0);
                // The bit inside the back ticks
                String newContentValue = contentValueMatcher.group(1);
                // De-escape dollars \$ => $
                newContentValue = newContentValue.replace("\\$", "$");
                // Escape dbl-quotes
                newContentValue = newContentValue.replace("\"", "\\\"");
                // Remove leading/trailing new lines
                newContentValue = LEADING_TRAILING_NEW_LINE_PATTERN.matcher(newContentValue).replaceAll("");
                // Escape line ends
                newContentValue = newContentValue.replace("\n", "\\n");
                newContentValue = "\"" + newContentValue + "\"";

                jsonStr3 = jsonStr3.replace(originalContentValue, newContentValue);
            }

//            System.out.println("--------------------------------------------------------------------------------");
//            System.out.println(jsonStr3);

            LOGGER.debug("jsonStr3:\n{}", jsonStr3);

            final List<Snippet> snippets = objectMapper.readValue(
                    jsonStr3,
                    new TypeReference<ArrayList<Snippet>>() {
                    });

            return snippets;

        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error parsing snippet file {} as javascript: {}",
                    snippetFile, LogUtil.exceptionMessage(e)), e);
        }
    }


    // --------------------------------------------------------------------------------


    private static class Snippets {

        @JsonProperty
        private final List<Snippet> snippets;

        @JsonCreator
        private Snippets(@JsonProperty("snippets") final List<Snippet> snippets) {
            this.snippets = snippets;
        }

        public List<Snippet> getSnippets() {
            return snippets;
        }
    }


    // --------------------------------------------------------------------------------


    private static class Snippet {

        @JsonProperty
        private final String tabTrigger;
        @JsonProperty
        private final String name;
        @JsonProperty
        private final String content;

        @JsonCreator
        private Snippet(@JsonProperty("tabTrigger") final String tabTrigger,
                        @JsonProperty("name") final String name,
                        @JsonProperty("content") final String content) {
            this.tabTrigger = tabTrigger;
            this.name = name;
            this.content = content;
        }

        public String getTabTrigger() {
            return tabTrigger;
        }

        public String getName() {
            return name;
        }

        public String getContent() {
            return content;
        }
    }


    // --------------------------------------------------------------------------------


    private record SnippetDefinition(
            String name,
            Path filePath,
            String description,
            String fencedBlockSyntaxType) {

    }
}
