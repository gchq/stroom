/*
 * Copyright 2018 Crown Copyright
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

package stroom.pipeline.server.reader;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.ElementIcons;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.Reader;

@Component
@Scope("prototype")
@ConfigurableElement(
        type = "TextReplacementFilterReader",
        category = Category.READER,
        roles = {
//                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.ROLE_READER,
                PipelineElementType.ROLE_MUTATOR,
                PipelineElementType.VISABILITY_STEPPING},
        icon = ElementIcons.STREAM)
public class TextReplacementFilterReaderElement extends AbstractReaderElement {
    private final ErrorReceiver errorReceiver;

    private TextReplacementFilterReader textReplacementFilterReader;

    private String regex;
    private String replacement;
    private Integer maxReplacements;
    private boolean literal;
    private boolean dotAll;
    private int bufferSize = 2000;

    @Inject
    public TextReplacementFilterReaderElement(final ErrorReceiverProxy errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    @Override
    protected Reader insertFilter(final Reader reader) {
        textReplacementFilterReader = new TextReplacementFilterReader.Builder()
                .reader(reader)
                .regex(regex)
                .replacement(replacement)
                .maxReplacements(maxReplacements)
                .literal(literal)
                .dotAll(dotAll)
                .bufferSize(bufferSize)
                .errorReceiver(errorReceiver)
                .elementId(getElementId())
                .build();
        return textReplacementFilterReader;
    }

    @Override
    public void endStream() {
        if (textReplacementFilterReader.getReplacementCount() > 0) {
            errorReceiver.log(
                    Severity.INFO,
                    null,
                    getElementId(),
                    "Performed " + textReplacementFilterReader.getReplacementCount() + " replacements",
                    null);
        }
    }

    @PipelineProperty(description = "The text or regex pattern to find and replace.")
    public void setPattern(final String regex) {
        this.regex = regex;
    }

    @PipelineProperty(description = "The replacement text.")
    public void setReplacement(final String replacement) {
        this.replacement = replacement;
    }

    @PipelineProperty(description = "The maximum number of times to try and replace text. There is no limit by default.")
    public void setMaxReplacements(final int maxReplacements) {
        this.maxReplacements = maxReplacements;
    }

    @PipelineProperty(description = "Whether the pattern should be treated as a literal or a regex.")
    public void setLiteral(final boolean literal) {
        this.literal = literal;
    }

    @PipelineProperty(description = "Let '.' match all characters in a regex.")
    public void setDotAll(final boolean dotAll) {
        this.dotAll = dotAll;
    }

    @PipelineProperty(description = "The number of characters to buffer when matching the regex.")
    public void setBufferSize(final int bufferSize) {
        this.bufferSize = bufferSize;
    }
}
