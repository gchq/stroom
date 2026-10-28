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

package stroom.activity.client.component;

import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.activity.shared.Activity.Prop;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Html;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.dom.client.TextAreaElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// An activity's details being edited, in a form whose HTML is set in the activity config: each
/// `input`, `textarea` or `select` with an `id` or `name` is one of the activity's properties.
public class ActivityEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Html html;

    /// Makes an empty form.
    public ActivityEdit() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Sets the form.
    ///
    /// @param body The form's HTML, from the activity config.
    public void setBody(final String body) {
        html.setHTML(body);
    }

    /// Fills the form's fields from an activity's properties, and focuses the first field.
    ///
    /// @param activity The activity, or null to leave the fields as they are.
    public void setActivity(final Activity activity) {
        boolean doneFocus = false;
        for (final Element element : findInputElements()) {
            // Focus the first input.
            if (!doneFocus) {
                doneFocus = true;
                element.focus();
            }

            final String value = getValue(activity, createProp(element, null).getId());
            if (value != null) {
                final String tagName = element.getTagName();
                if ("input".equalsIgnoreCase(tagName)) {
                    final InputElement inputElement = element.cast();
                    if (isTickable(inputElement)) {
                        inputElement.setChecked(Boolean.parseBoolean(value));
                    } else {
                        inputElement.setValue(value);
                    }
                } else if ("text".equalsIgnoreCase(tagName)) {
                    final InputElement inputElement = element.cast();
                    inputElement.setValue(value);
                } else if ("textarea".equalsIgnoreCase(tagName)) {
                    final TextAreaElement textAreaElement = element.cast();
                    textAreaElement.setValue(value);
                } else if ("select".equalsIgnoreCase(tagName)) {
                    final SelectElement selectElement = element.cast();
                    selectElement.setValue(value);
                }
            }
        }
    }

    /// @return The properties the form's fields hold.
    public ActivityDetails getDetails() {
        final List<Prop> properties = new ArrayList<>();
        for (final Element element : findInputElements()) {
            final String tagName = element.getTagName();
            if ("input".equalsIgnoreCase(tagName)) {
                final InputElement inputElement = element.cast();
                if (isTickable(inputElement)) {
                    properties.add(createProp(element, Boolean.toString(inputElement.isChecked())));
                } else {
                    properties.add(createProp(element, inputElement.getValue()));
                }
            } else if ("text".equalsIgnoreCase(tagName)) {
                final InputElement inputElement = element.cast();
                properties.add(createProp(element, inputElement.getValue()));
            } else if ("textarea".equalsIgnoreCase(tagName)) {
                final TextAreaElement textAreaElement = element.cast();
                properties.add(createProp(element, textAreaElement.getValue()));
            } else if ("select".equalsIgnoreCase(tagName)) {
                final SelectElement selectElement = element.cast();
                properties.add(createProp(element, selectElement.getValue()));
            }
        }
        return new ActivityDetails(properties);
    }

    private static boolean isTickable(final InputElement inputElement) {
        return "checkbox".equalsIgnoreCase(inputElement.getType())
               || "radio".equalsIgnoreCase(inputElement.getType());
    }

    private List<Element> findInputElements() {
        final List<Element> inputElements = new ArrayList<>();
        findInputElements(html.getElement().getChildNodes(), inputElements);
        return inputElements;
    }

    private void findInputElements(final NodeList<Node> nodes, final List<Element> inputElements) {
        for (int i = 0; i < nodes.getLength(); i++) {
            final Node node = nodes.getItem(i);
            if (node instanceof Element) {
                final Element element = (Element) node;
                final String tagName = element.getTagName();
                if ("input".equalsIgnoreCase(tagName)
                    || "text".equalsIgnoreCase(tagName)
                    || "textarea".equalsIgnoreCase(tagName)
                    || "select".equalsIgnoreCase(tagName)) {
                    if (createProp(element, null).getName() != null) {
                        inputElements.add(element);
                    }
                }
            }

            findInputElements(node.getChildNodes(), inputElements);
        }
    }

    private static Prop createProp(final Element element, final String value) {
        String id = getTrimmedAttribute(element, "id");
        String name = getTrimmedAttribute(element, "name");
        // Fall back to using name.
        id = Objects.requireNonNullElse(id, name);
        // Fall back to using id.
        name = Objects.requireNonNullElse(name, id);

        return Prop
                .builder()
                .id(id)
                .name(name)
                .value(value)
                .validation(getTrimmedAttribute(element, "validation"))
                .validationMessage(getTrimmedAttribute(element, "validationMessage"))
                .showInSelection(isTrueUnlessFalse(element, "showInSelection"))
                .showInList(isTrueUnlessFalse(element, "showInList"))
                .build();
    }

    private static String getTrimmedAttribute(final Element element, final String name) {
        final String value = element.getAttribute(name);
        if (value != null) {
            final String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private static boolean isTrueUnlessFalse(final Element element, final String name) {
        final String value = getTrimmedAttribute(element, name);
        return value == null || !value.equalsIgnoreCase("false");
    }

    private static String getValue(final Activity activity, final String propertyId) {
        if (activity != null && activity.getDetails() != null) {
            return activity.getDetails().value(propertyId);
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ActivityEdit> {

    }
}
