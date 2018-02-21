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

package stroom.pipeline.server.xsltfunctions;

import net.sf.saxon.Configuration;
import net.sf.saxon.value.SequenceType;
import stroom.pipeline.server.LocationFactory;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.spring.StroomBeanStore;

import java.util.ArrayList;
import java.util.List;

public class StroomXSLTFunctionLibrary {
    private final List<DelegateExtensionFunctionCall> callsInUse = new ArrayList<>();

    public StroomXSLTFunctionLibrary(final Configuration config) {
        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("bitmap-lookup")
                .library(this)
                .delegateClass(BitmapLookup.class)
                .minArgs(2)
                .maxArgs(4)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING,
                        SequenceType.SINGLE_STRING,
                        SequenceType.OPTIONAL_STRING,
                        SequenceType.OPTIONAL_BOOLEAN
                })
                .resultType(SequenceType.NODE_SEQUENCE)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("classification")
                .library(this)
                .delegateClass(Classification.class)
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("current-time")
                .library(this)
                .delegateClass(CurrentTime.class)
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("current-user")
                .library(this)
                .delegateClass(CurrentUser.class)
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("dictionary")
                .library(this)
                .delegateClass(Dictionary.class)
                .minArgs(1)
                .maxArgs(1)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING
                })
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        // TODO : Deprecate
        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("feed-attribute")
                .library(this)
                .delegateClass(Meta.class)
                .minArgs(1)
                .maxArgs(1)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING
                })
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("feed-name")
                .library(this)
                .delegateClass(FeedName.class)
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("fetch-json")
                .library(this)
                .minArgs(2)
                .maxArgs(2)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING,
                        SequenceType.SINGLE_STRING
                })
                .delegateClass(FetchJson.class)
                .resultType(SequenceType.NODE_SEQUENCE)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("format-date")
                .library(this)
                .delegateClass(FormatDate.class)
                .minArgs(1)
                .maxArgs(5)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING,
                        SequenceType.OPTIONAL_STRING,
                        SequenceType.OPTIONAL_STRING,
                        SequenceType.OPTIONAL_STRING,
                        SequenceType.OPTIONAL_STRING
                })
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("generate-url")
                .library(this)
                .minArgs(4)
                .maxArgs(4)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING,
                        SequenceType.SINGLE_STRING,
                        SequenceType.SINGLE_STRING,
                        SequenceType.SINGLE_STRING
                })
                .delegateClass(GenerateURL.class)
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("get")
                .library(this)
                .delegateClass(Get.class)
                .minArgs(1)
                .maxArgs(1)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING
                })
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("json-to-xml")
                .library(this)
                .delegateClass(JsonToXml.class)
                .minArgs(1)
                .maxArgs(1)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING
                })
                .resultType(SequenceType.NODE_SEQUENCE)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("log")
                .library(this)
                .delegateClass(Log.class)
                .minArgs(2)
                .maxArgs(2)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING,
                        SequenceType.SINGLE_STRING
                })
                .resultType(SequenceType.EMPTY_SEQUENCE)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("lookup")
                .library(this)
                .delegateClass(Lookup.class)
                .minArgs(2)
                .maxArgs(4)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING,
                        SequenceType.SINGLE_STRING,
                        SequenceType.OPTIONAL_STRING,
                        SequenceType.OPTIONAL_BOOLEAN
                })
                .resultType(SequenceType.NODE_SEQUENCE)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("meta")
                .library(this)
                .delegateClass(Meta.class)
                .minArgs(1)
                .maxArgs(1)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING
                })
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("numeric-ip")
                .library(this)
                .delegateClass(NumericIP.class)
                .minArgs(1)
                .maxArgs(1)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING
                })
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("parse-uri")
                .library(this)
                .delegateClass(ParseUri.class)
                .minArgs(1)
                .maxArgs(1)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING
                })
                .resultType(SequenceType.NODE_SEQUENCE)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("random")
                .library(this)
                .delegateClass(Random.class)
                .resultType(SequenceType.OPTIONAL_DOUBLE)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("search-id")
                .library(this)
                .delegateClass(SearchId.class)
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("stream-id")
                .library(this)
                .delegateClass(StreamId.class)
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("hex-to-dec")
                .library(this)
                .delegateClass(HexToDec.class)
                .minArgs(1)
                .maxArgs(1)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING
                })
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("hex-to-oct")
                .library(this)
                .delegateClass(HexToOct.class)
                .minArgs(1)
                .maxArgs(1)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING
                })
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("pipeline-name")
                .library(this)
                .delegateClass(PipelineName.class)
                .resultType(SequenceType.OPTIONAL_STRING)
                .build());

        config.registerExtensionFunction(DelegateExtensionFunctionDefinition.startBuild()
                .functionName("put")
                .library(this)
                .delegateClass(Put.class)
                .minArgs(2)
                .maxArgs(2)
                .argTypes(new SequenceType[]{
                        SequenceType.SINGLE_STRING,
                        SequenceType.SINGLE_STRING
                })
                .resultType(SequenceType.EMPTY_SEQUENCE)
                .build());
    }

    void registerInUse(final DelegateExtensionFunctionCall call) {
        callsInUse.add(call);
    }

    public void configure(final StroomBeanStore beanStore,
                          final ErrorReceiver errorReceiver,
                          final LocationFactory locationFactory,
                          final List<PipelineReference> pipelineReferences) {
        if (beanStore != null) {
            for (final DelegateExtensionFunctionCall call : callsInUse) {
                final Class<?> delegateClass = call.getDelegateClass();
                final StroomExtensionFunctionCall bean = (StroomExtensionFunctionCall) beanStore.getBean(delegateClass);
                bean.configure(errorReceiver, locationFactory, pipelineReferences);
                call.setDelegate(bean);
            }
        }
    }

    public void reset() {
        for (final DelegateExtensionFunctionCall call : callsInUse) {
            call.setDelegate(null);
        }
    }
}
