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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.xml.NamespaceConstants;

import jakarta.inject.Provider;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.value.SequenceType;

import java.util.List;

class StroomExtensionFunctionDefinition<T extends StroomExtensionFunctionCall> extends ExtensionFunctionDefinition {

    private final String functionName;
    private final int minArgs;
    private final int maxArgs;
    private final SequenceType[] argTypes;
    private final SequenceType resultType;
    private final transient StructuredQName qName;
    private final Provider<T> functionCallProvider;

    private ExtensionFunctionCallProxy proxy;

    StroomExtensionFunctionDefinition(final String functionName,
                                      final int minArgs,
                                      final int maxArgs,
                                      final SequenceType[] argTypes,
                                      final SequenceType resultType,
                                      final Provider<T> functionCallProvider) {
        this.functionName = functionName;
        this.minArgs = minArgs;
        this.maxArgs = maxArgs;
        this.argTypes = argTypes;
        this.resultType = resultType;
        this.functionCallProvider = functionCallProvider;

        qName = new StructuredQName("", NamespaceConstants.STROOM, functionName);
    }

    @Override
    public StructuredQName getFunctionQName() {
        return qName;
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return minArgs;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return maxArgs;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return argTypes;
    }

    @Override
    public SequenceType getResultType(final SequenceType[] suppliedArgumentTypes) {
        return resultType;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        if (proxy == null) {
            proxy = new ExtensionFunctionCallProxy(functionName);
        }
        return proxy;
    }

    void configure(final ErrorReceiver errorReceiver,
                   final LocationFactory locationFactory,
                   final List<PipelineReference> pipelineReferences) {
        if (proxy != null) {
            final StroomExtensionFunctionCall functionCall = functionCallProvider.get();
            functionCall.configure(errorReceiver, locationFactory, pipelineReferences);
            proxy.setFunctionCall(functionCall);
        }
    }

    void reset() {
        if (proxy != null) {
            proxy.setFunctionCall(null);
        }
    }
}
