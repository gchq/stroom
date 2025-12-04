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

package stroom.query.common.v2;

import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.concurrent.InterruptionUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;
import stroom.util.string.ExceptionStringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ErrorConsumerImpl implements ErrorConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ErrorConsumerImpl.class);

    private static final int MAX_ERROR_COUNT = 100;

    private final Set<ErrorMessage> errorMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicInteger errorCount = new AtomicInteger();

    public ErrorConsumerImpl() {
        LOGGER.debug("Creating errorConsumer {}", this);
    }

    @Override
    public void add(final Supplier<String> message) {
        add(Severity.ERROR, message);
    }

    @Override
    public void add(final Severity severity, final Supplier<String> message) {
        if (LOGGER.isTraceEnabled()) {
            try {
                throw new RuntimeException(message.get());
            } catch (final RuntimeException e) {
                LOGGER.trace(e::getMessage, e);
            }
        }

        final int count = errorCount.incrementAndGet();
        if (count <= MAX_ERROR_COUNT) {
            errorMessages.add(new ErrorMessage(severity, message.get()));
        }
    }

    @Override
    public void add(final Throwable exception) {
        if (!InterruptionUtil.isInterruption(exception)) {
            add(() -> ExceptionStringUtil.getMessage(exception));
        }
    }

    @Override
    public void clear() {
        errorMessages.clear();
        errorCount.set(0);
    }

    @Override
    public List<ErrorMessage> getErrorMessages() {
        if (!errorMessages.isEmpty()) {
            return new ArrayList<>(errorMessages);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public List<ErrorMessage> drain() {
        final List<ErrorMessage> copy = getErrorMessages();
        copy.forEach(errorMessages::remove);
        return copy;
    }

    @Override
    public boolean hasErrors() {
        return errorCount.get() > 0;
    }

    @Override
    public String toString() {
        return "id=" + System.identityHashCode(this)
               + " errorCount=" + errorCount.get();
    }
}
