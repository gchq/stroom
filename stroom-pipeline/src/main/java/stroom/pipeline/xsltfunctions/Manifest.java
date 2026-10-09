/*
 * Copyright 2025 Crown Copyright
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

import stroom.data.store.api.DataService;
import stroom.pipeline.state.MetaHolder;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;

import java.util.Objects;

@XsltFunctionDef(
        name = Manifest.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.PIPELINE,
        commonDescription = """
                Returns the manifest attributes of the current stream as an XML document
                in the `stroom-meta` namespace.
                The document has a `manifest` root element and a `string` element for each attribute,
                with its name in the `key` attribute. The attributes are ordered by name.

                If the stream cannot be read, an empty `manifest` element is returned.
                """,
        commonReturnType = XsltDataType.SEQUENCE,
        commonReturnDescription = "An XML document containing the stream's manifest attributes.",
        signatures = {
                @XsltFunctionSignature(
                        args = {})
        })
public class Manifest extends AbstractManifest {

    public static final String FUNCTION_NAME = "manifest";

    @Inject
    Manifest(final DataService dataService, final MetaHolder metaHolder) {
        super(dataService, metaHolder);
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        final long streamId = Objects.requireNonNull(
                metaHolder.getMetaId(),
                "MetaHolder.getMetaId() returned null");

        final Sequence result = getMetaSequence(context, streamId);

        return Objects.requireNonNullElseGet(result, () ->
                createEmptyMetaSequence(context, ELEMENT_NAME));
    }

}
