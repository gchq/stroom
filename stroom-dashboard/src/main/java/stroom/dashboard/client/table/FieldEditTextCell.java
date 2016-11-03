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

package stroom.dashboard.client.table;

import com.google.gwt.cell.client.AbstractEditableCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.BrowserEvents;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.client.SafeHtmlTemplates.Template;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.text.shared.SafeHtmlRenderer;

import stroom.widget.util.client.DoubleSelectTest;

/**
 * An editable text cell. Click to edit, escape to cancel, return to commit.
 */
public class FieldEditTextCell extends AbstractEditableCell<String, FieldEditTextCell.ViewData> {
    interface Template extends SafeHtmlTemplates {
        @Template("<input class=\"{0}\" type=\"text\" value=\"{1}\" tabindex=\"-1\"></input>")
        SafeHtml input(String className, String value);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, String value);
    }

    /**
     * The view data object used by this cell. We need to store both the text
     * and the state because this cell is rendered differently in edit mode. If
     * we did not store the edit state, refreshing the cell with view data would
     * always put us in to edit state, rendering a text box instead of the new
     * text string.
     */
    static class ViewData {
        private boolean isEditing;

        /**
         * If true, this is not the first edit.
         */
        private boolean isEditingAgain;

        /**
         * Keep track of the original value at the start of the edit, which
         * might be the edited value from the previous edit and NOT the actual
         * value.
         */
        private String original;

        private String text;

        /**
         * Construct a new ViewData in editing mode.
         *
         * @param text
         *            the text to edit
         */
        public ViewData(final String text) {
            this.original = text;
            this.text = text;
            this.isEditing = true;
            this.isEditingAgain = false;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null) {
                return false;
            }
            final ViewData vd = (ViewData) o;
            return equalsOrBothNull(original, vd.original) && equalsOrBothNull(text, vd.text)
                    && isEditing == vd.isEditing && isEditingAgain == vd.isEditingAgain;
        }

        public String getOriginal() {
            return original;
        }

        public String getText() {
            return text;
        }

        @Override
        public int hashCode() {
            return original.hashCode() + text.hashCode() + Boolean.valueOf(isEditing).hashCode() * 29
                    + Boolean.valueOf(isEditingAgain).hashCode();
        }

        public boolean isEditing() {
            return isEditing;
        }

        public boolean isEditingAgain() {
            return isEditingAgain;
        }

        public void setEditing(final boolean isEditing) {
            final boolean wasEditing = this.isEditing;
            this.isEditing = isEditing;

            // This is a subsequent edit, so start from where we left off.
            if (!wasEditing && isEditing) {
                isEditingAgain = true;
                original = text;
            }
        }

        public void setText(final String text) {
            this.text = text;
        }

        private boolean equalsOrBothNull(final Object o1, final Object o2) {
            return (o1 == null) ? o2 == null : o1.equals(o2);
        }
    }

    private static Template template;

    private final FieldsManager fieldsManager;
    private final DoubleSelectTest doubleClickTest = new DoubleSelectTest();

    /**
     * Construct a new EditTextCell that will use a given
     * {@link SafeHtmlRenderer} to render the value when not in edit mode.
     */
    public FieldEditTextCell(final FieldsManager fieldsManager) {
        super(BrowserEvents.CLICK, BrowserEvents.MOUSEDOWN, BrowserEvents.KEYUP, BrowserEvents.KEYDOWN,
                BrowserEvents.BLUR);
        this.fieldsManager = fieldsManager;
        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public boolean isEditing(final Context context, final Element parent, final String value) {
        final ViewData viewData = getViewData(context.getKey());
        return viewData != null && viewData.isEditing();
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final String value, final NativeEvent event,
            final ValueUpdater<String> valueUpdater) {
        final Object key = context.getKey();
        ViewData viewData = getViewData(key);
        if (viewData != null && viewData.isEditing()) {
            // Handle the edit event.
            editEvent(context, parent, value, viewData, event, valueUpdater);
        } else {
            final String type = event.getType();
            final int keyCode = event.getKeyCode();
            final boolean enterPressed = BrowserEvents.KEYUP.equals(type) && keyCode == KeyCodes.KEY_ENTER;

            if ((BrowserEvents.CLICK.equals(type) && doubleClickTest.test(parent)) || enterPressed) {
                // Go into edit mode.
                if (viewData == null) {
                    viewData = new ViewData(value);
                    setViewData(key, viewData);
                } else {
                    viewData.setEditing(true);
                }
                edit(context, parent, value);

                fieldsManager.setBusy(true);
            }
        }
    }

    @Override
    public void render(final Context context, final String value, final SafeHtmlBuilder sb) {
        // Get the view data.
        final Object key = context.getKey();
        ViewData viewData = getViewData(key);
        if (viewData != null && !viewData.isEditing() && value != null && value.equals(viewData.getText())) {
            clearViewData(key);
            viewData = null;
        }

        String toRender = value;
        if (viewData != null) {
            final String text = viewData.getText();
            if (viewData.isEditing()) {
                // Do not use the renderer in edit mode because the value of a
                // text input element is always treated as text. SafeHtml isn't
                // valid in the context of the value attribute.
                sb.append(template.input(fieldsManager.getResources().style().fieldText(), text));
                return;
            } else {
                // The user pressed enter, but view data still exists.
                toRender = text;
            }
        }

        if (toRender != null && toRender.trim().length() > 0) {
            sb.append(template.div(fieldsManager.getResources().style().fieldLabel(), toRender));
        } else {
            // Render a blank space to force the rendered element to have a
            // height. Otherwise it is not clickable.
            sb.appendHtmlConstant("\u00A0");
        }
    }

    @Override
    public boolean resetFocus(final Context context, final Element parent, final String value) {
        if (isEditing(context, parent, value)) {
            getInputElement(parent).focus();
            return true;
        }
        return false;
    }

    /**
     * Convert the cell to edit mode.
     *
     * @param context
     *            the {@link Context} of the cell
     * @param parent
     *            the parent element
     * @param value
     *            the current value
     */
    protected void edit(final Context context, final Element parent, final String value) {
        setValue(context, parent, value);
        final InputElement input = getInputElement(parent);
        input.focus();
        input.select();
    }

    /**
     * Convert the cell to non-edit mode.
     *
     * @param context
     *            the context of the cell
     * @param parent
     *            the parent Element
     * @param value
     *            the value associated with the cell
     */
    private void cancel(final Context context, final Element parent, final String value) {
        clearInput(getInputElement(parent));
        setValue(context, parent, value);
    }

    /**
     * Clear selected from the input element. Both Firefox and IE fire spurious
     * onblur events after the input is removed from the DOM if selection is not
     * cleared.
     *
     * @param input
     *            the input element
     */
    private native void clearInput(Element input)
    /*-{
    if (input.selectionEnd)
        input.selectionEnd = input.selectionStart;
    else if ($doc.selection)
        $doc.selection.clear();
    }-*/;

    /**
     * Commit the current value.
     *
     * @param context
     *            the context of the cell
     * @param parent
     *            the parent Element
     * @param viewData
     *            the {@link ViewData} object
     * @param valueUpdater
     *            the {@link ValueUpdater}
     */
    private void commit(final Context context, final Element parent, final ViewData viewData,
            final ValueUpdater<String> valueUpdater) {
        final String value = updateViewData(parent, viewData, false);
        clearInput(getInputElement(parent));
        setValue(context, parent, viewData.getOriginal());
        if (valueUpdater != null) {
            valueUpdater.update(value);
        }
    }

    private void editEvent(final Context context, final Element parent, final String value, final ViewData viewData,
            final NativeEvent event, final ValueUpdater<String> valueUpdater) {
        final String type = event.getType();
        final boolean keyUp = BrowserEvents.KEYUP.equals(type);
        final boolean keyDown = BrowserEvents.KEYDOWN.equals(type);
        if (keyUp || keyDown) {
            final int keyCode = event.getKeyCode();
            if (keyUp && keyCode == KeyCodes.KEY_ENTER) {
                // Commit the change.
                commit(context, parent, viewData, valueUpdater);

                fieldsManager.setBusy(false);
            } else if (keyUp && keyCode == KeyCodes.KEY_ESCAPE) {
                // Cancel edit mode.
                final String originalText = viewData.getOriginal();
                if (viewData.isEditingAgain()) {
                    viewData.setText(originalText);
                    viewData.setEditing(false);
                } else {
                    setViewData(context.getKey(), null);
                }
                cancel(context, parent, value);

                fieldsManager.setBusy(false);
            } else {
                // Update the text in the view data on each key.
                updateViewData(parent, viewData, true);

                // fieldsManager.setBusy(true);
            }
        } else if (BrowserEvents.BLUR.equals(type)) {
            // Commit the change. Ensure that we are blurring the input element
            // and not the parent element itself.
            final EventTarget eventTarget = event.getEventTarget();
            if (Element.is(eventTarget)) {
                final Element target = Element.as(eventTarget);
                if ("input".equals(target.getTagName().toLowerCase())) {
                    commit(context, parent, viewData, valueUpdater);

                    fieldsManager.setBusy(false);
                }
            }
        }
    }

    /**
     * Get the input element in edit mode.
     */
    private InputElement getInputElement(final Element parent) {
        return parent.getFirstChild().cast();
    }

    /**
     * Update the view data based on the current value.
     *
     * @param parent
     *            the parent element
     * @param viewData
     *            the {@link ViewData} object to update
     * @param isEditing
     *            true if in edit mode
     * @return the new value
     */
    private String updateViewData(final Element parent, final ViewData viewData, final boolean isEditing) {
        final InputElement input = (InputElement) parent.getFirstChild();
        final String value = input.getValue();
        viewData.setText(value);
        viewData.setEditing(isEditing);
        return value;
    }
}
