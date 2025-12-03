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

import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.meta.shared.Meta;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.StringValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

class ParentForId extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "parent-for-id";

    private final Store store;

    private Long lastStreamId = null;
    private Long lastParentId = null;

    @Inject
    ParentForId(final Store store) {
        this.store = store;
    }

    @Override
    protected Sequence call(final String functionName,
                            final XPathContext context,
                            final Sequence[] arguments) {
        String result = null;

        try {
            final long streamId = Long.parseLong(getSafeString(functionName, context, arguments, 0));

            final Optional<Long> parentId = getParentId(streamId);
            if (parentId.isPresent()) {
                result = String.valueOf(parentId.get());
            }
        } catch (final Exception e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }


    private Optional<Long> getParentId(final long streamId) {
        if (lastStreamId != null && lastStreamId.equals(streamId)) {
            return Optional.ofNullable(lastParentId);
        }

        try (final Source source = store.openSource(streamId)) {
            final Meta meta = source.getMeta();
            if (meta != null) {
                lastParentId = meta.getParentMetaId();
                lastStreamId = streamId;
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return Optional.ofNullable(lastParentId);
    }
}
