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

import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.widget.xsdbrowser.client.XsdNode.XsdType;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerManager;
import com.google.gwt.xml.client.Document;
import com.google.gwt.xml.client.Element;
import com.google.gwt.xml.client.Node;
import com.google.gwt.xml.client.NodeList;
import com.google.gwt.xml.client.XMLParser;
import com.google.gwt.xml.client.impl.DOMParseException;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XsdModel implements HasDataSelectionHandlers<XsdNode> {

    private final HandlerManager handlerManager = new HandlerManager(this);
    List<XsdNode> history = new ArrayList<>();
    int historyPos = -1;
    private Document doc;
    private Map<String, XsdNode> globalTypeMap;
    private Map<String, XsdNode> globalElementMap;
    private Map<String, XsdNode> globalGroupMap;
    private XsdNode currentItem;
    private XsdNode selectedItem;
    private DOMParseException parseException;

    public void home() {
        // Nothing has been shown yet, e.g. the schema is empty
        if (history.isEmpty()) {
            return;
        }
        historyPos = 0;
        setSelectedItem(history.get(historyPos), true, false);
    }

    public void back() {
        // Nothing has been shown yet, e.g. the schema is empty
        if (history.isEmpty()) {
            return;
        }
        historyPos--;
        if (historyPos < 0) {
            historyPos = 0;
        }

        setSelectedItem(history.get(historyPos), true, false);
    }

    public void forward() {
        // Nothing has been shown yet, e.g. the schema is empty
        if (history.isEmpty()) {
            return;
        }
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
                // Some browsers (e.g. Chrome) don't fail to parse malformed XML; they recover, and
                // report the error in a parsererror element in the partial document
                final String parserError = getParserError(doc);
                if (parserError != null) {
                    doc = null;
                    parseException = new DOMParseException(parserError);
                }
            } catch (final DOMParseException e) {
                parseException = e;
            }
        }
        showRoot();
    }

    private static String getParserError(final Document doc) {
        final NodeList parserErrors = doc.getElementsByTagName("parsererror");
        if (parserErrors.getLength() == 0) {
            return null;
        }
        // Chrome's parsererror holds a heading, the error in a div, and another heading
        final Element parserError = (Element) parserErrors.item(0);
        final NodeList details = parserError.getElementsByTagName("div");
        final String text = getText(details.getLength() > 0
                ? details.item(0)
                : parserError).trim();
        return text.isEmpty()
                ? "Unable to parse the schema"
                : text;
    }

    private static String getText(final Node node) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            return node.getNodeValue();
        }
        final StringBuilder sb = new StringBuilder();
        final NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            sb.append(getText(children.item(i)));
        }
        return sb.toString();
    }

    private void showRoot() {
        history.clear();

        XsdNode selectedItem = null;

        if (doc != null && doc.hasChildNodes()) {
            for (int i = 0; i < doc.getChildNodes().getLength(); i++) {
                final XsdNode node = new XsdNode(this, doc.getChildNodes().item(i));

                if (node.getType() == XsdType.SCHEMA) {
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

        if (selectedItem != null) {
            setSelectedItem(selectedItem, true, true);
        } else {
            // No schema (e.g. the contents couldn't be parsed), so the display is told to change, to
            // clear the last schema and show any parse error
            this.currentItem = null;
            this.selectedItem = null;
            DataSelectionEvent.fire(this, null, true);
        }
    }

    private void createGlobalMaps(final XsdNode node) {
        globalTypeMap = new HashMap<>();
        globalElementMap = new HashMap<>();
        globalGroupMap = new HashMap<>();

        for (final XsdNode child : node.getChildNodes()) {
            final XsdType type = child.getType();
            final String name = child.getName();
            if (name != null) {
                if (type == XsdType.ELEMENT) {
                    globalElementMap.put(name, child);
                } else if (type == XsdType.GROUP) {
                    globalGroupMap.put(name, child);
                } else if (type == XsdType.COMPLEX_TYPE || type == XsdType.SIMPLE_TYPE) {
                    globalTypeMap.put(name, child);
                }
            }
        }
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<XsdNode> handler) {
        return handlerManager.addHandler(DataSelectionEvent.getType(), handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        handlerManager.fireEvent(event);
    }

    public XsdNode getSelectedItem() {
        return selectedItem;
    }

    public DOMParseException getParseException() {
        return parseException;
    }

    public void setSelectedItem(final XsdNode selectedItem, final boolean doubleSelect) {
        setSelectedItem(selectedItem, doubleSelect, true);
    }

    private void setSelectedItem(final XsdNode selectedItem, final boolean doubleSelect, final boolean addToHistory) {
        if (doubleSelect) {
            if (selectedItem != null
                    && (selectedItem.getType() == XsdType.SCHEMA || selectedItem.getType() == XsdType.ELEMENT
                    || selectedItem.getType() == XsdType.COMPLEX_TYPE)) {
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

    public Map<String, XsdNode> getGlobalTypeMap() {
        return globalTypeMap;
    }

    public Map<String, XsdNode> getGlobalElementMap() {
        return globalElementMap;
    }

    public Map<String, XsdNode> getGlobalGroupMap() {
        return globalGroupMap;
    }
}
