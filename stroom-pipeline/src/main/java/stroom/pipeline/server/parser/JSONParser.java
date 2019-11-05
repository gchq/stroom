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

package stroom.pipeline.server.parser;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.XMLReader;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.spring.StroomScope;
import stroom.xml.converter.json.JSONParserFactory;
import stroom.xml.converter.json.JSONFactoryConfig;

import javax.inject.Inject;

@Component
@Scope(value = StroomScope.TASK)
@ConfigurableElement(type = "JSONParser", category = Category.PARSER, roles = {PipelineElementType.ROLE_PARSER,
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE,
        PipelineElementType.VISABILITY_STEPPING, PipelineElementType.ROLE_MUTATOR}, icon = ElementIcons.JSON)
public class JSONParser extends AbstractParser {
    private JSONFactoryConfig config = new JSONFactoryConfig();
    private boolean addRootObject = true;

    @Inject
    public JSONParser(final ErrorReceiverProxy errorReceiverProxy, final LocationFactoryProxy locationFactory) {
        super(errorReceiverProxy, locationFactory);
    }

    @Override
    protected XMLReader createReader() {
        final JSONParserFactory jsonParserFactory = new JSONParserFactory();
        jsonParserFactory.setConfig(config);
        jsonParserFactory.setAddRootObject(addRootObject);
        return jsonParserFactory.getParser();
    }

    @PipelineProperty(description = "Add a root map element.",
            defaultValue = "true")
    public void setAddRootObject(final boolean addRootObject) {
        this.addRootObject = addRootObject;
    }

    @PipelineProperty(description = "Feature that determines whether parser will allow use  of Java/C++ style comments" +
            " (both '/'+'*' and '//' varieties) within parsed content or not.",
            defaultValue = "false")
    public void setAllowComments(final boolean allowComments) {
        this.config.setAllowComments(allowComments);
    }

    @PipelineProperty(description = "Feature that determines whether parser will allow use of YAML comments, ones " +
            "starting with '#' and continuing until the end of the line. This commenting style is common with scripting " +
            "languages as well.",
            defaultValue = "false")
    public void setAllowYamlComments(final boolean allowYamlComments) {
        this.config.setAllowYamlComments(allowYamlComments);
    }

    @PipelineProperty(description = "Feature that determines whether parser will allow use of unquoted field names " +
            "(which is allowed by Javascript, but not by JSON specification).",
            defaultValue = "false")
    public void setAllowUnquotedFieldNames(final boolean allowUnquotedFieldNames) {
        this.config.setAllowUnquotedFieldNames(allowUnquotedFieldNames);
    }

    @PipelineProperty(description = "Feature that determines whether parser will allow use of single quotes (apostrophe," +
            " character '\\'') for quoting Strings (names and String values). If so, this is in addition to other" +
            " acceptable markers but not by JSON specification).",
            defaultValue = "false")
    public void setAllowSingleQuotes(final boolean allowSingleQuotes) {
        this.config.setAllowSingleQuotes(allowSingleQuotes);
    }

    @PipelineProperty(description = "Feature that determines whether parser will allow JSON Strings to contain unquoted" +
            " control characters (ASCII characters with value less than 32, including tab and line feed characters) or" +
            " not. If feature is set false, an exception is thrown if such a character is encountered.",
            defaultValue = "false")
    public void setAllowUnquotedControlChars(final boolean allowUnquotedControlChars) {
        this.config.setAllowUnquotedControlChars(allowUnquotedControlChars);
    }

    @PipelineProperty(description = "Feature that can be enabled to accept quoting of all character using backslash" +
            " quoting mechanism: if not enabled, only characters that are explicitly listed by JSON specification can" +
            " be thus escaped (see JSON spec for small list of these characters)",
            defaultValue = "false")
    public void setAllowBackslashEscapingAnyCharacter(final boolean allowBackslashEscapingAnyCharacter) {
        this.config.setAllowBackslashEscapingAnyCharacter(allowBackslashEscapingAnyCharacter);
    }

    @PipelineProperty(description = "Feature that determines whether parser will allow JSON integral numbers to start" +
            " with additional (ignorable) zeroes (like: 000001).",
            defaultValue = "false")
    public void setAllowNumericLeadingZeros(final boolean allowNumericLeadingZeros) {
        this.config.setAllowNumericLeadingZeros(allowNumericLeadingZeros);
    }

    @PipelineProperty(description = "Feature that allows parser to recognize set of \"Not-a-Number\" (NaN) tokens as" +
            " legal floating number values (similar to how many other data formats and programming language source" +
            " code allows it).",
            defaultValue = "false")
    public void setAllowNonNumericNumbers(final boolean allowNonNumericNumbers) {
        this.config.setAllowNonNumericNumbers(allowNonNumericNumbers);
    }

    @PipelineProperty(description = "Feature allows the support for \"missing\" values in a JSON array: missing value" +
            " meaning sequence of two commas, without value in-between but only optional white space.",
            defaultValue = "false")
    public void setAllowMissingValues(final boolean allowMissingValues) {
        this.config.setAllowMissingValues(allowMissingValues);
    }

    @PipelineProperty(description = "Feature that determines whether we will allow for a single trailing comma" +
            " following the final value (in an Array) or member (in an Object). These commas will simply be ignored.",
            defaultValue = "false")
    public void setAllowTrailingComma(final boolean allowTrailingComma) {
        this.config.setAllowTrailingComma(allowTrailingComma);
    }
}
