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

package stroom.pipeline.reader;

import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.apache.commons.text.StringEscapeUtils;

import java.io.Reader;

@ConfigurableElement(
        type = "FindReplaceFilter",
        description = """
                Replaces strings or regexes with new strings.
                """,
        category = Category.READER,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.ROLE_READER,
                PipelineElementType.ROLE_MUTATOR,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_STREAM)
public class FindReplaceFilterElement extends AbstractReaderElement {

    private final LocationFactory locationFactory;
    private final ErrorReceiver errorReceiver;

    private FindReplaceFilter textReplacementFilterReader;

    private String find;
    private String replacement = "";
    private boolean escapeFind = true;
    private boolean escapeReplacement = true;
    private int maxReplacements = -1;
    private boolean regex;
    private boolean dotAll;
    private int bufferSize = 1000;
    private boolean showReplacementCount = true;

    @Inject
    public FindReplaceFilterElement(final LocationFactory locationFactory,
                                    final ErrorReceiverProxy errorReceiver) {
        this.locationFactory = locationFactory;
        this.errorReceiver = errorReceiver;
    }

    @Override
    protected Reader insertFilter(final Reader reader) {
        Reader result = reader;

        try {
            textReplacementFilterReader = FindReplaceFilter.builder()
                    .reader(reader)
                    .find(escapeFind
                            ? StringEscapeUtils.unescapeJava(find)
                            : find)
                    .replacement(escapeReplacement
                            ? StringEscapeUtils.unescapeJava(replacement)
                            : replacement)
                    .maxReplacements(maxReplacements)
                    .regex(regex)
                    .dotAll(dotAll)
                    .bufferSize(bufferSize)
                    .locationFactory(locationFactory)
                    .errorReceiver(errorReceiver)
                    .elementId(getElementId())
                    .build();
            result = textReplacementFilterReader;

        } catch (final RuntimeException e) {
            errorReceiver.log(
                    Severity.ERROR,
                    null,
                    getElementId(),
                    e.getMessage(),
                    e);
        }

        return result;
    }

    @Override
    public void endStream() {
        if (textReplacementFilterReader != null) {
            if (showReplacementCount && textReplacementFilterReader.getTotalReplacementCount() > 0) {
                errorReceiver.log(
                        Severity.INFO,
                        null,
                        getElementId(),
                        "Performed " + textReplacementFilterReader.getTotalReplacementCount() + " replacements",
                        null);
            }

            // Reset some of the variables so we can find/replace again in the next stream.
            textReplacementFilterReader.clear();
        }
        super.endStream();
    }

    @PipelineProperty(
            description = "The text or regex pattern to find and replace.",
            displayPriority = 1)
    public void setFind(final String find) {
        this.find = find;
    }

    @PipelineProperty(
            description = "The replacement text.",
            displayPriority = 2)
    public void setReplacement(final String replacement) {
        this.replacement = replacement;
    }

    @PipelineProperty(
            description = "Whether or not to escape find pattern or text.",
            defaultValue = "true",
            displayPriority = 3)
    public void setEscapeFind(final boolean escapeFind) {
        this.escapeFind = escapeFind;
    }

    @PipelineProperty(
            description = "Whether or not to escape replacement text.",
            defaultValue = "true",
            displayPriority = 4)
    public void setEscapeReplacement(final boolean escapeReplacement) {
        this.escapeReplacement = escapeReplacement;
    }

    @PipelineProperty(
            description = "The maximum number of times to try and replace text. There is no limit by default.",
            displayPriority = 5)
    public void setMaxReplacements(final String maxReplacements) {
        try {
            this.maxReplacements = Integer.parseInt(maxReplacements);
        } catch (final NumberFormatException e) {
            this.maxReplacements = -1;
            // Ignore.
        }
    }

    @PipelineProperty(
            description = "Whether the pattern should be treated as a literal or a regex.",
            defaultValue = "false",
            displayPriority = 6)
    public void setRegex(final boolean regex) {
        this.regex = regex;
    }

    @PipelineProperty(
            description = "Let '.' match all characters in a regex.",
            defaultValue = "false",
            displayPriority = 7)
    public void setDotAll(final boolean dotAll) {
        this.dotAll = dotAll;
    }

    @PipelineProperty(
            description = "The number of characters to buffer when matching the regex.",
            defaultValue = "1000",
            displayPriority = 8)
    public void setBufferSize(final int bufferSize) {
        this.bufferSize = bufferSize;
    }

    @PipelineProperty(
            description = "Show total replacement count",
            defaultValue = "true",
            displayPriority = 9)
    public void setShowReplacementCount(final boolean showReplacementCount) {
        this.showReplacementCount = showReplacementCount;
    }
}
