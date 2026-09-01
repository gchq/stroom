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

package stroom.pipeline.xsltfunctions;

import stroom.ai.api.AiService;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code stroom:ai($model, $message)} and {@code stroom:ai($model, $message, $systemPrompt)}. Asks the chat
 * model identified by name or UUID a question and returns its answer.
 * <p>
 * The model is asked once per call, so a pipeline that calls this for every record will make a request per
 * record. Repeated identical questions are served from a cache, see {@code AiConfig.chatResponseCache}.
 */
class Ai extends StroomExtensionFunctionCall {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Ai.class);

    public static final String FUNCTION_NAME = "ai";

    private final AiService aiService;

    /**
     * The models we have already resolved, so that a per-record call does not repeat the lookup. Holds
     * not-found results too, so that a bad model reference does not warn once per record.
     */
    private Map<String, Optional<DocRef>> cachedModelRefs;

    @Inject
    Ai(final AiService aiService) {
        this.aiService = aiService;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            final String model = getSafeString(functionName, context, arguments, 0);
            final String message = getSafeString(functionName, context, arguments, 1);
            final String systemPrompt = arguments.length > 2
                    ? getSafeString(functionName, context, arguments, 2)
                    : null;

            if (model == null || model.isEmpty()) {
                log(context, Severity.WARNING, "No model specified for AI call", null);

            } else if (message == null || message.isEmpty()) {
                log(context, Severity.WARNING, "No message specified for AI call", null);

            } else {
                final Optional<DocRef> optionalModelRef = getModelRef(context, model);
                if (optionalModelRef.isPresent()) {
                    result = aiService.chat(optionalModelRef.get(), systemPrompt, message);
                }
            }
        } catch (final XPathException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }

    private Optional<DocRef> getModelRef(final XPathContext context, final String model) {
        if (cachedModelRefs == null) {
            cachedModelRefs = new HashMap<>();
        }

        final Optional<DocRef> cached = cachedModelRefs.get(model);
        if (cached != null) {
            return cached;
        }

        // Let any exception propagate to the caller, which reports it as an error. Deliberately not cached,
        // as caching a transient failure would silently produce no output for every later record.
        final Optional<DocRef> optionalDocRef = aiService.findModelByNameOrUuid(model);
        LOGGER.debug(() -> "Resolved AI model '" + model + "' to " + optionalDocRef);

        if (optionalDocRef.isEmpty()) {
            log(context, Severity.WARNING, "AI model not found with name or UUID '" + model
                                           + "'. You might not have permission to use this model", null);
        }

        cachedModelRefs.put(model, optionalDocRef);
        return optionalDocRef;
    }
}
