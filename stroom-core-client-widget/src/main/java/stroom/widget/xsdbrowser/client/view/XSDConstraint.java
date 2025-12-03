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

import stroom.widget.xsdbrowser.client.view.XSDNode.XSDAttribute;
import stroom.widget.xsdbrowser.client.view.XSDNode.XSDType;

import java.util.ArrayList;
import java.util.List;

public class XSDConstraint {

    private XSDNode restrictionNode;
    private List<String> patternList;
    private List<String> enumerationList;

    public XSDConstraint(final XSDNode node) {
        if (node != null && node.getType() == XSDType.SIMPLE_TYPE) {
            restrictionNode = getRestrictionNode(node);
            if (restrictionNode != null) {
                getValueLists(restrictionNode);
            }
        }
    }

    private XSDNode getRestrictionNode(final XSDNode node) {
        if (node != null) {
            for (final XSDNode child : node.getChildNodes(new XSDTypeFilter(XSDType.RESTRICTION), true)) {
                return child;
            }
        }

        return null;
    }

    private void getValueLists(final XSDNode node) {
        patternList = new ArrayList<>();
        enumerationList = new ArrayList<>();

        if (node != null) {
            for (final XSDNode child : node.getChildNodes(XSDTypeFilter.RESTRICTION_TYPE_FILTER, true)) {
                if (child.getType() == XSDType.PATTERN) {
                    final String value = XMLUtil.getAttributeValue(child.getNode(), XSDAttribute.VALUE, false);
                    if (value != null) {
                        patternList.add(value);
                    }
                } else if (child.getType() == XSDType.ENUMERATION) {
                    final String value = XMLUtil.getAttributeValue(child.getNode(), XSDAttribute.VALUE, false);
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
