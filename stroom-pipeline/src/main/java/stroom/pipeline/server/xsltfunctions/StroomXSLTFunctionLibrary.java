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
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomBeanStore;

import java.util.ArrayList;
import java.util.List;

public class StroomXSLTFunctionLibrary {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StroomXSLTFunctionLibrary.class);

    private final Configuration config;
    private final List<DelegateExtensionFunctionCall> callsInUse = new ArrayList<>();

    public StroomXSLTFunctionLibrary(final Configuration config) {
        this.config = config;
        try {
            register("bitmap-lookup", BitmapLookup.class, 2, 4, new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING, SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_BOOLEAN}, SequenceType.NODE_SEQUENCE);
            register("classification", Classification.class, 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING);
            register("current-time", CurrentTime.class, 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING);
            register("current-user", CurrentUser.class, 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING);
            register("dictionary", Dictionary.class, 1, 1, new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.OPTIONAL_STRING);

            // TODO : Deprecate
            register("feed-attribute", Meta.class, 1, 1, new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.OPTIONAL_STRING);

            register("feed-name", FeedName.class, 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING);
            register("format-date", FormatDate.class, 1, 5, new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_STRING}, SequenceType.OPTIONAL_STRING);
            register("get", Get.class, 1, 1, new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.OPTIONAL_STRING);
            register("json-to-xml", JsonToXml.class, 1, 1, new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.NODE_SEQUENCE);
            register("log", Log.class, 2, 2, new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING}, SequenceType.EMPTY_SEQUENCE);
            register("lookup", Lookup.class, 2, 4, new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING, SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_BOOLEAN}, SequenceType.NODE_SEQUENCE);
            register("meta", Meta.class, 1, 1, new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.OPTIONAL_STRING);
            register("numeric-ip", NumericIP.class, 1, 1, new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.OPTIONAL_STRING);
            register("random", Random.class, 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_DOUBLE);
            register("search-id", SearchId.class, 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING);
            register("stream-id", StreamId.class, 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING);
            register("hex-to-dec", HexToDec.class, 1, 1, new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.OPTIONAL_STRING);
            register("hex-to-oct", HexToOct.class, 1, 1, new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.OPTIONAL_STRING);
            register("pipeline-name", PipelineName.class, 0, 0, new SequenceType[]{}, SequenceType.OPTIONAL_STRING);
            register("put", Put.class, 2, 2, new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.SINGLE_STRING}, SequenceType.EMPTY_SEQUENCE);

        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private <T extends StroomExtensionFunctionCall> void register(final String functionName, final Class<T> clazz,
                                                                  final int minArgs, final int maxArgs, final SequenceType[] argTypes, final SequenceType resultType) {
        final DelegateExtensionFunctionDefinition function = new DelegateExtensionFunctionDefinition(this, functionName,
                minArgs, maxArgs, argTypes, resultType, clazz);

        config.registerExtensionFunction(function);
    }

    void registerInUse(final DelegateExtensionFunctionCall call) {
        callsInUse.add(call);
    }

    public void configure(final StroomBeanStore beanStore, final ErrorReceiver errorReceiver,
                          final LocationFactory locationFactory, final List<PipelineReference> pipelineReferences) {
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
