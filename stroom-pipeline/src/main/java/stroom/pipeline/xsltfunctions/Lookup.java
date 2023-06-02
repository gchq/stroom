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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.refdata.LookupIdentifier;
import stroom.pipeline.refdata.ReferenceData;
import stroom.pipeline.refdata.ReferenceDataResult;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.state.MetaHolder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;

import com.google.common.base.Strings;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.Actor;
import net.sf.saxon.expr.instruct.CallTemplate;
import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import javax.inject.Inject;

class Lookup extends AbstractLookup {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Lookup.class);

    public static final String FUNCTION_NAME = "lookup";

    @Inject
    Lookup(final ReferenceData referenceData,
           final MetaHolder metaHolder,
           final SequenceMakerFactory sequenceMakerFactory) {
        super(referenceData, metaHolder, sequenceMakerFactory);
    }

    private void dump(final Object object,
                      final Set<Object> done,
                      final int depth) {
//        CompletableFuture.runAsync(() -> {
            if (object instanceof Actor) {
                final Actor actor = (Actor) object;
                System.out.println("rule location: " + actor.getLocation().getLineNumber() + ":" + actor.getLocation().getColumnNumber());
            }

            try {
                final Method[] methods = object.getClass().getMethods();

                // Get values.
                for (final Method method : methods) {
                    if (method.getParameterCount() == 0 && method.getName().startsWith("get") && method.trySetAccessible()) {
                        System.out.println(Strings.padStart("",
                                depth * 3,
                                ' ') + object.getClass().getName() + " - " + method.getName());
                        try {
                            Object result = method.invoke(object);
                            if (result != null) {
                                try {
                                    if (result.getClass().isPrimitive()) {
                                        System.out.println(result);
                                    }
                                    if (result instanceof Number) {
                                        final Number number = (Number) result;
                                        int l = number.intValue();
                                        if ( l > 2 && l < 1000) {
                                            System.out.println(l);
                                        }
                                    }

                                } catch (final Throwable e) {
                                }
                            }
                        } catch (final Throwable e) {
                            System.err.println(e.getMessage());
                        }
                    }
                }

                // Recurse.
                for (final Method method : methods) {
                    if (method.getParameterCount() == 0 && method.getName().startsWith("get")) {
                        System.out.println(Strings.padStart("",
                                depth * 3,
                                ' ') + object.getClass().getName() + " - " + method.getName());
                        try {
                            Object result = method.invoke(object);
                            if (result != null) {
                                if (!done.contains(result) && depth < 40) {
                                    done.add(result);
                                    dump(result, done, depth + 1);
                                }
                            }
                        } catch (final Throwable e) {
                            System.err.println(e.getMessage());
                        }
                    }
                }
            } catch (final Throwable e) {
                System.err.println(e.getMessage());
            }
//        });
    }

    @Override
    protected Sequence doLookup(final XPathContext context,
                                final boolean ignoreWarnings,
                                final boolean trace,
                                final LookupIdentifier lookupIdentifier) {
        dump(context, Collections.newSetFromMap(new ConcurrentHashMap<>()), 0);

//        if (context.getCaller() instanceof XPathContextMajor) {
//            final XPathContextMajor xPathContextMajor = (XPathContextMajor) context.getCaller();
//            if (xPathContextMajor.getOrigin() instanceof CallTemplate) {
//                final CallTemplate callTemplate = (CallTemplate) xPathContextMajor.getOrigin();
//                callTemplate.getTargetTemplate()
//                System.out.println("rule location: " + callTemplate.getLocation().getLineNumber() + ":" + callTemplate.getLocation().getColumnNumber());
//            }
//        }

//        if (context.getCaller().getCurrentTemplateRule().getAction() instanceof TemplateRule) {
//            final TemplateRule templateRule = (TemplateRule) context.getCaller().getCurrentTemplateRule().getAction();
//            System.out.println("rule location: " + templateRule.getLineNumber() + ":" + templateRule.getColumnNumber());
//        }

        LOGGER.debug(() -> LogUtil.message("Looking up {}, {}",
                lookupIdentifier, Instant.ofEpochMilli(lookupIdentifier.getEventTime())));

        // TODO rather than putting the proxy in the result we could just put the refStreamDefinition
        // in there and then do the actual lookup in the sequenceMaker by passing an injected RefDataStore
        // into it.
        final ReferenceDataResult result = getReferenceData(lookupIdentifier, trace, ignoreWarnings);

        // We have to create this even if we don't have any value proxies so we can output an empty sequence
        final SequenceMaker sequenceMaker = createSequenceMaker(context);

        // Note, for a nested lookup the (effective|qualifying)Streams will contain the streams for the last
        // level of the nested lookup, but the messages will cover all levels.
        try {
            if (result.getRefDataValueProxy().isPresent()) {
                // Map exists in one/more eff streams so try looking up the key
                consumeValue(context, ignoreWarnings, trace, result, sequenceMaker);
            } else {
                // No value proxy so log the reason
                logFailureReason(result, context, ignoreWarnings, trace);
            }
        } catch (final Exception e) {
            outputInfo(
                    Severity.ERROR,
                    () -> "Error during lookup: " + e.getMessage(),
                    lookupIdentifier,
                    trace,
                    ignoreWarnings,
                    result,
                    context);
        }

        return sequenceMaker.toSequence();
    }

    private void consumeValue(final XPathContext context,
                              final boolean ignoreWarnings,
                              final boolean trace,
                              final ReferenceDataResult result,
                              final SequenceMaker sequenceMaker) throws XPathException {
        boolean wasFound;
        sequenceMaker.open();

        //noinspection OptionalGetWithoutIsPresent // checked outside method
        final RefDataValueProxy refDataValueProxy = result.getRefDataValueProxy().get();

        logMapLocations(result, refDataValueProxy);

        wasFound = sequenceMaker.consume(refDataValueProxy);

        logLookupValue(wasFound, result, context, ignoreWarnings, trace);

        sequenceMaker.close();
    }

}
