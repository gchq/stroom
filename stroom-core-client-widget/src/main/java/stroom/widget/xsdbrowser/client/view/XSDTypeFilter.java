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

package stroom.widget.xsdbrowser.client.view;

import stroom.widget.xsdbrowser.client.view.XSDNode.XSDType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class XSDTypeFilter {

    public static final XSDTypeFilter ATTRIBUTE_FILTER = new XSDTypeFilter(XSDType.ATTRIBUTE);
    public static final XSDTypeFilter TYPE_FILTER = new XSDTypeFilter(XSDType.COMPLEX_TYPE, XSDType.SIMPLE_TYPE);
    public static final XSDTypeFilter COMPLEX_CONTENT_FILTER = new XSDTypeFilter(XSDType.COMPLEX_CONTENT);
    public static final XSDTypeFilter EXTENSION_FILTER = new XSDTypeFilter(XSDType.EXTENSION);
    public static final XSDTypeFilter STRUCTURE_FILTER = new XSDTypeFilter(XSDType.SEQUENCE, XSDType.CHOICE,
            XSDType.ALL, XSDType.ELEMENT, XSDType.GROUP);
    public static final XSDTypeFilter COMPLEX_TYPE_FILTER = new XSDTypeFilter(XSDType.COMPLEX_TYPE);
    public static final XSDTypeFilter SIMPLE_TYPE_FILTER = new XSDTypeFilter(XSDType.SIMPLE_TYPE);
    public static final XSDTypeFilter DOCUMENTATION_FILTER = new XSDTypeFilter(XSDType.ANNOTATION,
            XSDType.DOCUMENTATION);
    public static final XSDTypeFilter RESTRICTION_TYPE_FILTER = new XSDTypeFilter(XSDType.PATTERN, XSDType.ENUMERATION);

    private final Set<XSDType> typeSet = new HashSet<>();

    public XSDTypeFilter(final XSDType... types) {
        typeSet.addAll(Arrays.asList(types));
    }

    public boolean contains(final XSDType type) {
        return typeSet.contains(type);
    }
}
