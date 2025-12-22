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

package stroom.pipeline.parser;

import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.xml.converter.json.JSONFactoryConfig;
import stroom.pipeline.xml.converter.json.JSONParserFactory;
import stroom.svg.shared.SvgImage;

import jakarta.inject.Inject;
import org.xml.sax.XMLReader;

@ConfigurableElement(
        type = "JSONParser",
        category = Category.PARSER,
        description = """
                A built-in parser for parsing JSON source data (in JSON fragment format) into an XML representation \
                of the JSON.
                The Resulting XML will conform to the http://www.w3.org/2013/XSL/json namespace.
                """,
        roles = {
                PipelineElementType.ROLE_PARSER,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE,
                PipelineElementType.VISABILITY_STEPPING,
                PipelineElementType.ROLE_MUTATOR},
        icon = SvgImage.PIPELINE_JSON)
public class JSONParser extends AbstractParser {

    private JSONFactoryConfig config = new JSONFactoryConfig();
    private boolean addRootObject = true;

    @Inject
    public JSONParser(final ErrorReceiverProxy errorReceiverProxy,
                      final LocationFactoryProxy locationFactory) {
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
            defaultValue = "true",
            displayPriority = 1)
    public void setAddRootObject(final boolean addRootObject) {
        this.addRootObject = addRootObject;
    }

    @PipelineProperty(
            description = "Feature that determines whether parser will allow use  of Java/C++ style " +
                    "comments (both '/'+'*' and '//' varieties) within parsed content or not.",
            defaultValue = "false",
            displayPriority = 2)
    public void setAllowComments(final boolean allowComments) {
        this.config.setAllowComments(allowComments);
    }

    @PipelineProperty(
            description = "Feature that determines whether parser will allow use of YAML comments, ones " +
                    "starting with '#' and continuing until the end of the line. This commenting style is " +
                    "common with scripting languages as well.",
            defaultValue = "false",
            displayPriority = 3)
    public void setAllowYamlComments(final boolean allowYamlComments) {
        this.config.setAllowYamlComments(allowYamlComments);
    }

    @PipelineProperty(description = "Feature that determines whether parser will allow use of unquoted field names " +
            "(which is allowed by Javascript, but not by JSON specification).",
            defaultValue = "false",
            displayPriority = 4)
    public void setAllowUnquotedFieldNames(final boolean allowUnquotedFieldNames) {
        this.config.setAllowUnquotedFieldNames(allowUnquotedFieldNames);
    }

    @PipelineProperty(
            description = "Feature that determines whether parser will allow use of single quotes (apostrophe," +
                    " character '\\'') for quoting Strings (names and String values). If so, this is in addition " +
                    "to other acceptable markers but not by JSON specification).",
            defaultValue = "false",
            displayPriority = 5)
    public void setAllowSingleQuotes(final boolean allowSingleQuotes) {
        this.config.setAllowSingleQuotes(allowSingleQuotes);
    }

    @PipelineProperty(
            description = "Feature that determines whether parser will allow JSON Strings to contain unquoted" +
                    " control characters (ASCII characters with value less than 32, including tab and line " +
                    "feed characters) or not. If feature is set false, an exception is thrown if such a " +
                    "character is encountered.",
            defaultValue = "false",
            displayPriority = 6)
    public void setAllowUnquotedControlChars(final boolean allowUnquotedControlChars) {
        this.config.setAllowUnquotedControlChars(allowUnquotedControlChars);
    }

    @PipelineProperty(description = "Feature that can be enabled to accept quoting of all character using backslash" +
            " quoting mechanism: if not enabled, only characters that are explicitly listed by JSON specification can" +
            " be thus escaped (see JSON spec for small list of these characters)",
            defaultValue = "false",
            displayPriority = 7)
    public void setAllowBackslashEscapingAnyCharacter(final boolean allowBackslashEscapingAnyCharacter) {
        this.config.setAllowBackslashEscapingAnyCharacter(allowBackslashEscapingAnyCharacter);
    }

    @PipelineProperty(description = "Feature that determines whether parser will allow JSON integral numbers to start" +
            " with additional (ignorable) zeroes (like: 000001).",
            defaultValue = "false",
            displayPriority = 8)
    public void setAllowNumericLeadingZeros(final boolean allowNumericLeadingZeros) {
        this.config.setAllowNumericLeadingZeros(allowNumericLeadingZeros);
    }

    @PipelineProperty(description = "Feature that allows parser to recognize set of \"Not-a-Number\" (NaN) tokens as" +
            " legal floating number values (similar to how many other data formats and programming language source" +
            " code allows it).",
            defaultValue = "false",
            displayPriority = 9)
    public void setAllowNonNumericNumbers(final boolean allowNonNumericNumbers) {
        this.config.setAllowNonNumericNumbers(allowNonNumericNumbers);
    }

    @PipelineProperty(description = "Feature allows the support for \"missing\" values in a JSON array: missing value" +
            " meaning sequence of two commas, without value in-between but only optional white space.",
            defaultValue = "false",
            displayPriority = 10)
    public void setAllowMissingValues(final boolean allowMissingValues) {
        this.config.setAllowMissingValues(allowMissingValues);
    }

    @PipelineProperty(description = "Feature that determines whether we will allow for a single trailing comma" +
            " following the final value (in an Array) or member (in an Object). These commas will simply be ignored.",
            defaultValue = "false",
            displayPriority = 11)
    public void setAllowTrailingComma(final boolean allowTrailingComma) {
        this.config.setAllowTrailingComma(allowTrailingComma);
    }
}
