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

package stroom.pipeline.server.filter;

import javax.xml.namespace.NamespaceContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class NamespaceContextImpl implements NamespaceContext {
    private Map<String, String> prefixToUriMap = new HashMap<>();
    private Map<String, Set<String>> uriToPrefixMap = new HashMap<>();

    @Override
    public String getNamespaceURI(final String prefix) {
        return prefixToUriMap.get(prefix);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public String getPrefix(final String namespaceURI) {
        final Iterator iter = getPrefixes(namespaceURI);
        if (iter == null) {
            return null;
        }
        return (String) iter.next();
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Iterator getPrefixes(final String namespaceURI) {
        final Set<String> prefixSet = uriToPrefixMap.get(namespaceURI);
        if (prefixSet == null || prefixSet.size() == 0) {
            return null;
        }

        return prefixSet.iterator();
    }

    public void addPrefix(final String prefix, final String namespaceURI) {
        prefixToUriMap.put(prefix, namespaceURI);

        // Add to URI to prefix map.
        Set<String> prefixSet = uriToPrefixMap.get(namespaceURI);
        if (prefixSet == null) {
            prefixSet = new HashSet<>();
            uriToPrefixMap.put(namespaceURI, prefixSet);
        }
        prefixSet.add(prefix);
    }

    public void removePrefix(final String prefix) {
        final String namespaceURI = prefixToUriMap.remove(prefix);

        // Remove from URI to prefix map.
        if (namespaceURI != null) {
            Set<String> prefixSet = uriToPrefixMap.get(namespaceURI);
            if (prefixSet != null) {
                prefixSet.remove(prefix);
                if (prefixSet.size() == 0) {
                    uriToPrefixMap.remove(namespaceURI);
                }
            }
        }
    }

    public void clear() {
        prefixToUriMap.clear();
        uriToPrefixMap.clear();
    }
}
