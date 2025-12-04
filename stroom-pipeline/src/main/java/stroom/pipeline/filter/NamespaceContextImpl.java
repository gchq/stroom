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

package stroom.pipeline.filter;

import stroom.util.shared.NullSafe;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.NamespaceContext;

/**
 * Holds the namespaces and their prefixes encountered in the output of the element being stepped
 */
public class NamespaceContextImpl implements NamespaceContext {

    private final Map<String, String> prefixToUriMap = new HashMap<>();
    private final Map<String, Set<String>> uriToPrefixMap = new HashMap<>();

    @Override
    public String getNamespaceURI(final String prefix) {
        return prefixToUriMap.get(prefix);
    }

    @Override
    public String getPrefix(final String namespaceURI) {
        return NullSafe.get(
                getPrefixes(namespaceURI),
                Iterator::next);
    }

    @Override
    public Iterator<String> getPrefixes(final String namespaceURI) {
        final Set<String> prefixSet = uriToPrefixMap.get(namespaceURI);
        return NullSafe.isEmptyCollection(prefixSet)
                ? null
                : prefixSet.iterator();

    }

    public void addPrefix(final String prefix, final String namespaceURI) {
        // putIfAbsent because we may encounter namespace declarations deeper in the document which would
        // override the ones from the top level element, e.g
        // <Events
        //         xmlns="event-logging:3"
        //         xmlns:sm="stroom-meta"
        //         ...
        //         Version="3.4.2">
        //   <Event>
        //     <Meta>
        //       <source xmlns="stroom-meta">
        //
        // the 'stroom-meta' namespace with null prefix would override 'event-logging:3' with null prefix.
        // We would expect the user to do a xpath like '/Events/Event/Meta/sm:source'
        prefixToUriMap.putIfAbsent(prefix, namespaceURI);

        // Add to URI to prefix map.
        uriToPrefixMap.computeIfAbsent(namespaceURI, k -> new HashSet<>())
                .add(prefix);
    }

    public void removePrefix(final String prefix) {
        final String namespaceURI = prefixToUriMap.remove(prefix);

        // Remove from URI to prefix map.
        if (namespaceURI != null) {
            final Set<String> prefixSet = uriToPrefixMap.get(namespaceURI);
            if (prefixSet != null) {
                prefixSet.remove(prefix);
                if (prefixSet.isEmpty()) {
                    uriToPrefixMap.remove(namespaceURI);
                }
            }
        }
    }

    public void clear() {
        prefixToUriMap.clear();
        uriToPrefixMap.clear();
    }

    @Override
    public String toString() {
        return "NamespaceContextImpl{" +
               "prefixToUriMap=" + prefixToUriMap +
               '}';
    }
}
