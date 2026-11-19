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

package stroom.xmlschema.client.xsdbrowser;

import stroom.xmlschema.client.xsdbrowser.XsdNode.XsdAttribute;
import stroom.xmlschema.client.xsdbrowser.XsdNode.XsdType;

import java.util.ArrayList;
import java.util.List;

public class XsdConstraint {

    private XsdNode restrictionNode;
    private List<String> patternList;
    private List<String> enumerationList;

    public XsdConstraint(final XsdNode node) {
        if (node != null && node.getType() == XsdType.SIMPLE_TYPE) {
            restrictionNode = getRestrictionNode(node);
            if (restrictionNode != null) {
                getValueLists(restrictionNode);
            }
        }
    }

    private XsdNode getRestrictionNode(final XsdNode node) {
        if (node != null) {
            for (final XsdNode child : node.getChildNodes(new XsdTypeFilter(XsdType.RESTRICTION), true)) {
                return child;
            }
        }

        return null;
    }

    private void getValueLists(final XsdNode node) {
        patternList = new ArrayList<>();
        enumerationList = new ArrayList<>();

        if (node != null) {
            for (final XsdNode child : node.getChildNodes(XsdTypeFilter.RESTRICTION_TYPE_FILTER, true)) {
                if (child.getType() == XsdType.PATTERN) {
                    final String value = XmlUtil.getAttributeValue(child.getNode(), XsdAttribute.VALUE, false);
                    if (value != null) {
                        patternList.add(value);
                    }
                } else if (child.getType() == XsdType.ENUMERATION) {
                    final String value = XmlUtil.getAttributeValue(child.getNode(), XsdAttribute.VALUE, false);
                    if (value != null) {
                        enumerationList.add(value);
                    }
                }
            }
        }
    }

    /**
     * @return the patternList
     */
    public List<String> getPatternList() {
        return patternList;
    }

    /**
     * @return the enumerationList
     */
    public List<String> getEnumerationList() {
        return enumerationList;
    }
}
