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

package stroom.cell.dropdowntree.client;

import static com.google.gwt.dom.client.BrowserEvents.CLICK;
import static com.google.gwt.dom.client.BrowserEvents.KEYDOWN;

import com.google.gwt.cell.client.AbstractEditableCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.CssResource.ImportedWithPrefix;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.AbstractImagePrototype;

import stroom.util.shared.HasDisplayValue;

public abstract class DropDownCell<E> extends AbstractEditableCell<E, E> {
    @ImportedWithPrefix("dropDownTreeCell")
    public interface Styles extends CssResource {
        String dropDownTree();

        String label();

        String button();
    }

    public interface Resources extends ClientBundle {
        ImageResource popup();

        @Source("dropdowntree.css")
        Styles styles();
    }

    public interface Template extends SafeHtmlTemplates {
        @Template("<div class=\"{0}\"><div class=\"{1}\" title=\"{3}\">{3}</div><div class=\"{2}\">{4}</div></div>")
        SafeHtml input(String outerStyle, String labelStyle, String buttonStyle, String value, SafeHtml icon);
    }

    private static volatile Resources resources;
    private static volatile Template template;
    private static volatile SafeHtml button;

    private Object lastKey;
    private Element lastParent;
    private int lastIndex;
    private int lastColumn;
    private E lastValue;
    private ValueUpdater<E> valueUpdater;

    public DropDownCell() {
        super(CLICK, KEYDOWN);

        if (resources == null) {
            synchronized (DropDownCell.class) {
                if (resources == null) {
                    resources = GWT.create(Resources.class);
                    resources.styles().ensureInjected();
                    template = GWT.create(Template.class);
                    button = AbstractImagePrototype.create(resources.popup()).getSafeHtml();
                }
            }
        }
    }

    @Override
    public boolean dependsOnSelection() {
        return false;
    }

    @Override
    public boolean handlesSelection() {
        return false;
    }

    @Override
    public void setValue(final Context context, final Element parent, final E value) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        render(context, value, sb);
        if (parent != null) {
            parent.setInnerHTML(sb.toSafeHtml().asString());
        }
    }

    public void setValue(final E value) {
        // Remember the values before hiding the popup.
        final Element cellParent = lastParent;
        final E oldValue = lastValue;
        final Object key = lastKey;
        final int index = lastIndex;
        final int column = lastColumn;

        // Update the cell and value updater.
        final E date = value;
        setViewData(key, date);
        setValue(new Context(index, column, key), cellParent, oldValue);
        if (valueUpdater != null) {
            valueUpdater.update(date);
        }

        lastKey = null;
        lastValue = null;
        lastIndex = -1;
        lastColumn = -1;
        if (lastParent != null) {
            // Refocus on the containing cell after the user selects a value.
            lastParent.focus();
        }
        lastParent = null;
    }

    @Override
    public boolean isEditing(final Context context, final Element parent, final E value) {
        return lastKey != null && lastKey.equals(context.getKey());
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final E value, final NativeEvent event,
            final ValueUpdater<E> valueUpdater) {
        super.onBrowserEvent(context, parent, value, event, valueUpdater);
        if (CLICK.equals(event.getType())) {
            onEnterKeyDown(context, parent, value, event, valueUpdater);
        }
    }

    @Override
    public void render(final Context context, final E value, final SafeHtmlBuilder sb) {
        // Get the view data.
        final Object key = context.getKey();
        E viewData = getViewData(key);
        if (viewData != null && viewData.equals(value)) {
            clearViewData(key);
            viewData = null;
        }

        HasDisplayValue displayValue = null;
        if (viewData != null) {
            displayValue = (HasDisplayValue) viewData;
        } else if (value != null) {
            displayValue = (HasDisplayValue) value;
        }

        if (displayValue != null) {
            sb.append(template.input(resources.styles().dropDownTree(), resources.styles().label(),
                    resources.styles().button(), displayValue.getDisplayValue(), button));
        } else {
            sb.append(template.input(resources.styles().dropDownTree(), resources.styles().label(),
                    resources.styles().button(), getUnselectedText(), button));
        }
    }

    @Override
    protected void onEnterKeyDown(final Context context, final Element parent, final E value, final NativeEvent event,
            final ValueUpdater<E> valueUpdater) {
        this.lastKey = context.getKey();
        this.lastParent = parent;
        this.lastValue = value;
        this.lastIndex = context.getIndex();
        this.lastColumn = context.getColumn();
        this.valueUpdater = valueUpdater;

        final E viewData = getViewData(lastKey);
        final E date = (viewData == null) ? lastValue : viewData;

        Scheduler.get().scheduleDeferred(new ScheduledCommand() {
            @Override
            public void execute() {
                showPopup(date);
            }
        });
    }

    protected abstract String getUnselectedText();

    protected abstract void showPopup(E value);
}
