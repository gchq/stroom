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

package stroom.pipeline.xml.converter.ds3.ref;

import stroom.pipeline.xml.converter.ds3.StoreFactory;
import stroom.pipeline.xml.converter.ds3.StoreNode;
import stroom.pipeline.xml.converter.ds3.VarFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RefResolver {
    private final String reference;
    private final List<RefFactory> factories;

    // Hidden constructor.
    private RefResolver(final String reference, final List<RefFactory> factories) {
        this.reference = reference;
        this.factories = factories;
    }

    public static RefResolver create(final VarFactoryMap varFactoryMap, final StoreFactory owner,
                                     final String reference, final Set<String> localVars) {
        // Parse the reference string.
        final RefParser refParser = new RefParser();
        final List<RefFactory> factories = refParser.parse(reference);

        // Tell each referenced node factory that it is referenced and make sure
        // we can locate referenced variables.
        for (final RefFactory factory : factories) {
            if (!factory.isText()) {
                final StoreRefFactory refDesc = (StoreRefFactory) factory;
                final String refId = refDesc.getRefId();

                if (refId == null) {
                    // This is a self reference. Tell the referenced factory
                    // that it is referenced so it knows to store data.
                    owner.addReferencedGroup(refDesc.getRefGroup(), true);
                    refDesc.setLocal(true);

                } else {
                    // Find the referenced variable.
                    final VarFactory varFactory = varFactoryMap.get(refId);
                    if (varFactory == null) {
                        throw new RuntimeException("Unable to find variable \"" + refId + "\" referenced from \""
                                + owner.getDebugId() + "\"");
                    }

                    // Determine if this is a local reference.
                    boolean localRef = false;
                    if (localVars != null && localVars.contains(refId)) {
                        localRef = true;
                    }

                    // Tell the referenced variable that it is referenced so it
                    // knows to store data.
                    varFactory.addReferencedGroup(refDesc.getRefGroup(), localRef);
                    refDesc.setLocal(localRef);
                }
            }
        }

        return new RefResolver(reference, factories);
    }

    public Ref createRef(final VarMap varMap, final StoreNode owner) {
        if (factories.size() == 0) {
            return new NullRef();
        } else if (factories.size() == 1) {
            final RefFactory factory = factories.get(0);
            return getRef(factory, varMap, owner);
        } else {
            final List<Ref> refs = new ArrayList<>();
            for (final RefFactory factory : factories) {
                refs.add(getRef(factory, varMap, owner));
            }
            return new CompositeRef(refs);
        }
    }

    private Ref getRef(final RefFactory factory, final VarMap varMap, final StoreNode owner) {
        if (factory.isText()) {
            final TextRefFactory textRefFactory = (TextRefFactory) factory;
            return textRefFactory.createRef();

        } else {
            final StoreRefFactory storeRefFactory = (StoreRefFactory) factory;
            return storeRefFactory.createRef(varMap, owner);
        }
    }

    @Override
    public String toString() {
        return reference;
    }
}
