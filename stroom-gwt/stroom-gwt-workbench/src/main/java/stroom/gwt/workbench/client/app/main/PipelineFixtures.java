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

import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;

/// Pipeline fixtures shared by the pipeline stories (`PipelineEditor`, `PipelineTree`,
/// `SteppingScreen`, `StepFilterDialog`): the element types Stroom's `PipelineElementTypesFactory`
/// fetches (`GET /pipeline/v1/propertyTypes`), shaped as the gwt-suite corpus records them, with the
/// React stories' element types and property descriptions, and a stepper's replies.
final class PipelineFixtures {

    /// The path of `PipelineResource.getPropertyTypes()`.
    static final String PROPERTY_TYPES_PATH = "/pipeline/v1/propertyTypes";

    private static final String SOURCE = """
            {"type": "Source", "roles": ["source", "hasTargets", "simple"], "icon": "PIPELINE_STREAM",
              "displayValue": "Source"}""";

    private static final String COMBINED_PARSER = """
            {"type": "CombinedParser", "category": "PARSER",
              "roles": ["parser", "hasTargets", "simple", "stepping", "mutator", "hasCode"],
              "icon": "PIPELINE_TEXT", "displayValue": "Combined Parser"}""";

    private static final String XSLT_FILTER = """
            {"type": "XSLTFilter", "category": "FILTER",
              "roles": ["target", "hasTargets", "simple", "stepping", "mutator", "hasCode"],
              "icon": "PIPELINE_XSLT", "displayValue": "XSLT Filter"}""";

    private static final String XML_WRITER = """
            {"type": "XMLWriter", "category": "WRITER", "roles": ["target", "hasTargets", "writer", "mutator",
              "stepping"], "icon": "PIPELINE_XML", "displayValue": "XML Writer"}""";

    private static final String STREAM_APPENDER = """
            {"type": "StreamAppender", "category": "DESTINATION", "roles": ["target", "destination", "stepping"],
              "icon": "PIPELINE_STREAM", "displayValue": "Stream Appender"}""";

    /// The reply of `GET /pipeline/v1/propertyTypes`: Source, Combined Parser, XSLT Filter (with its
    /// `xslt` document and `reference` pipeline reference properties), XML Writer and Stream
    /// Appender.
    static final String PROPERTY_TYPES = """
            [
              {"pipelineElementType": SOURCE, "propertyTypes": {}},
              {"pipelineElementType": COMBINED_PARSER, "propertyTypes": {}},
              {"pipelineElementType": XSLT_FILTER, "propertyTypes": {
                "xslt": {"elementType": XSLT_FILTER, "name": "xslt", "type": "DocRef",
                  "description": "The XSLT to use", "defaultValue": "", "pipelineReference": false,
                  "docRefTypes": ["XSLT"], "displayPriority": 1, "canEmbed": true},
                "reference": {"elementType": XSLT_FILTER, "name": "reference", "type": "PipelineReference",
                  "description": "Reference loaders", "defaultValue": "", "pipelineReference": true,
                  "displayPriority": 2, "canEmbed": false}
              }},
              {"pipelineElementType": XML_WRITER, "propertyTypes": {}},
              {"pipelineElementType": STREAM_APPENDER, "propertyTypes": {}}
            ]"""
            .replace("SOURCE", SOURCE)
            .replace("COMBINED_PARSER", COMBINED_PARSER)
            .replace("XSLT_FILTER", XSLT_FILTER)
            .replace("XML_WRITER", XML_WRITER)
            .replace("STREAM_APPENDER", STREAM_APPENDER);

    /// The data of stream 101's first record, as `DataResource.fetch()` returns it.
    static final String DATA = """
            {"type": "data", "feedName": "MY_FEED", "streamTypeName": "Raw Events",
              "classification": "UNKNOWN CLASSIFICATION",
              "sourceLocation": {"metaId": 101, "partIndex": 0, "recordIndex": 0,
                "dataRange": {"locationFrom": {"type": "default", "lineNo": 1, "colNo": 1}, "charOffsetFrom": 0,
                  "byteOffsetFrom": 0, "locationTo": {"type": "default", "lineNo": 1, "colNo": 5},
                  "charOffsetTo": 4, "byteOffsetTo": 4, "length": 5},
                "highlights": []},
              "itemRange": {"offset": 0, "length": 1}, "totalItemCount": {"count": 5, "exact": true},
              "totalCharacterCount": {"count": 5, "exact": true}, "totalBytes": 5,
              "availableChildStreamTypes": [null], "data": "<in/>", "html": false,
              "dataType": "NON_SEGMENTED", "displayMode": "TEXT"}""";

    private PipelineFixtures() {
        // Constants
    }

    /// Routes answering a stepper (`SteppingResource`, and the data of the stream being stepped): each
    /// step completes at once, finding stream 101's first record with input and output for the
    /// element given.
    ///
    /// @param elementId The id of the element the step data is for.
    /// @return The routes.
    static RestFixtures stepping(final String elementId) {
        final String result = """
                {"sessionUuid": "sess-1", "complete": true, "foundRecord": true,
                  "foundLocation": {"metaId": 101, "partIndex": 0, "recordIndex": 0},
                  "stepData": {"sourceLocation": {"metaId": 101, "partIndex": 0, "recordIndex": 0},
                    "elementMap": {"ELEMENT": {"input": "<in/>", "output": "<out/>"}}}}"""
                .replace("ELEMENT", elementId);
        return RestFixtures.builder()
                .post("/stepping/v1/step", RestReply.json(result))
                .post("/stepping/v1/terminateStepping", RestReply.json("true"))
                .post("/stepping/v1/findElementDoc", RestReply.json("null"))
                .post("/data/v1/fetch", RestReply.json(DATA))
                .get("/data/v1/101/parts/0/child-types", RestReply.json("[null]"))
                .get("/meta/v1/101", RestReply.json("{\"id\": 101, \"feedName\": \"MY_FEED\", "
                        + "\"typeName\": \"Raw Events\", \"status\": \"UNLOCKED\", \"createMs\": 1700000000000}"))
                .build();
    }
}
