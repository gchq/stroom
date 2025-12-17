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

import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.widget.xsdbrowser.client.view.XSDNode.XSDType;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.xml.client.Document;
import com.google.gwt.xml.client.XMLParser;
import com.google.gwt.xml.client.impl.DOMParseException;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XSDModel implements HasDataSelectionHandlers<XSDNode> {

    private final HandlerManager handlerManager = new HandlerManager(this);
    List<XSDNode> history = new ArrayList<>();
    int historyPos = -1;
    private Document doc;
    private Map<String, XSDNode> globalTypeMap;
    private Map<String, XSDNode> globalElementMap;
    private Map<String, XSDNode> globalGroupMap;
    private XSDNode currentItem;
    private XSDNode selectedItem;
    private DOMParseException parseException;

    public void home() {
        historyPos = 0;
        setSelectedItem(history.get(historyPos), true, false);
    }

    public void back() {
        historyPos--;
        if (historyPos < 0) {
            historyPos = 0;
        }

        setSelectedItem(history.get(historyPos), true, false);
    }

    public void forward() {
        historyPos++;
        if (historyPos >= history.size()) {
            historyPos = history.size() - 1;
        }

        setSelectedItem(history.get(historyPos), true, false);
    }

    public void setContents(final String contents) {
        doc = null;
        parseException = null;
        selectedItem = null;

        if (contents != null) {
            try {
                doc = XMLParser.parse(contents);
            } catch (final DOMParseException e) {
                parseException = e;
            }
        }
        showRoot();
    }

    private void showRoot() {
        history.clear();

        XSDNode selectedItem = null;

        if (doc != null && doc.hasChildNodes()) {
            for (int i = 0; i < doc.getChildNodes().getLength(); i++) {
                final XSDNode node = new XSDNode(this, doc.getChildNodes().item(i));

                if (node.getType() == XSDType.SCHEMA) {
                    // Populate maps used for element and type lookups.
                    createGlobalMaps(node);
                    selectedItem = node;
                }
            }
        } else {
            globalTypeMap = new HashMap<>();
            globalElementMap = new HashMap<>();
            globalGroupMap = new HashMap<>();
        }

        setSelectedItem(selectedItem, true, selectedItem != null);
    }

    private void createGlobalMaps(final XSDNode node) {
        globalTypeMap = new HashMap<>();
        globalElementMap = new HashMap<>();
        globalGroupMap = new HashMap<>();

        for (final XSDNode child : node.getChildNodes()) {
            final XSDType type = child.getType();
            final String name = child.getName();
            if (name != null) {
                if (type == XSDType.ELEMENT) {
                    globalElementMap.put(name, child);
                } else if (type == XSDType.GROUP) {
                    globalGroupMap.put(name, child);
                } else if (type == XSDType.COMPLEX_TYPE || type == XSDType.SIMPLE_TYPE) {
                    globalTypeMap.put(name, child);
                }
            }
        }
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<XSDNode> handler) {
        return handlerManager.addHandler(DataSelectionEvent.getType(), handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        handlerManager.fireEvent(event);
    }

    public XSDNode getSelectedItem() {
        return selectedItem;
    }

    public DOMParseException getParseException() {
        return parseException;
    }

    public void setSelectedItem(final XSDNode selectedItem, final boolean doubleSelect) {
        setSelectedItem(selectedItem, doubleSelect, true);
    }

    private void setSelectedItem(final XSDNode selectedItem, final boolean doubleSelect, final boolean addToHistory) {
        if (doubleSelect) {
            if (selectedItem != null
                    && (selectedItem.getType() == XSDType.SCHEMA || selectedItem.getType() == XSDType.ELEMENT
                    || selectedItem.getType() == XSDType.COMPLEX_TYPE)) {
                if (addToHistory) {
                    // Add this node to the history.
                    historyPos++;
                    while (history.size() > historyPos) {
                        history.remove(history.size() - 1);
                    }

                    history.add(selectedItem);
                }

                this.currentItem = selectedItem;
                this.selectedItem = selectedItem;
                DataSelectionEvent.fire(this, selectedItem, true);

            } else {
                this.selectedItem = selectedItem;
                DataSelectionEvent.fire(this, selectedItem, false);
            }
        } else {
            this.selectedItem = selectedItem;
            DataSelectionEvent.fire(this, selectedItem, false);
        }
    }

    public Map<String, XSDNode> getGlobalTypeMap() {
        return globalTypeMap;
    }

    public Map<String, XSDNode> getGlobalElementMap() {
        return globalElementMap;
    }

    public Map<String, XSDNode> getGlobalGroupMap() {
        return globalGroupMap;
    }
}
