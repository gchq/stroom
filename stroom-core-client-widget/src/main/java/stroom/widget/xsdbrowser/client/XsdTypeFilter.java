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

package stroom.widget.xsdbrowser.client;

import stroom.widget.xsdbrowser.client.XsdNode.XsdType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class XsdTypeFilter {

    public static final XsdTypeFilter ATTRIBUTE_FILTER = new XsdTypeFilter(XsdType.ATTRIBUTE);
    public static final XsdTypeFilter TYPE_FILTER = new XsdTypeFilter(XsdType.COMPLEX_TYPE, XsdType.SIMPLE_TYPE);
    public static final XsdTypeFilter COMPLEX_CONTENT_FILTER = new XsdTypeFilter(XsdType.COMPLEX_CONTENT);
    public static final XsdTypeFilter EXTENSION_FILTER = new XsdTypeFilter(XsdType.EXTENSION);
    public static final XsdTypeFilter STRUCTURE_FILTER = new XsdTypeFilter(XsdType.SEQUENCE, XsdType.CHOICE,
            XsdType.ALL, XsdType.ELEMENT, XsdType.GROUP);
    public static final XsdTypeFilter COMPLEX_TYPE_FILTER = new XsdTypeFilter(XsdType.COMPLEX_TYPE);
    public static final XsdTypeFilter SIMPLE_TYPE_FILTER = new XsdTypeFilter(XsdType.SIMPLE_TYPE);
    public static final XsdTypeFilter DOCUMENTATION_FILTER = new XsdTypeFilter(XsdType.ANNOTATION,
            XsdType.DOCUMENTATION);
    public static final XsdTypeFilter RESTRICTION_TYPE_FILTER = new XsdTypeFilter(XsdType.PATTERN, XsdType.ENUMERATION);

    private final Set<XsdType> typeSet = new HashSet<>();

    public XsdTypeFilter(final XsdType... types) {
        typeSet.addAll(Arrays.asList(types));
    }

    public boolean contains(final XsdType type) {
        return typeSet.contains(type);
    }
}
