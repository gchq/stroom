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

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import stroom.dashboard.client.table.FieldsManager.Style;

public class FieldsCell extends AbstractCell<Boolean> {
    private static Template template;
    private final FieldsManager fieldsManager;

    public FieldsCell(final FieldsManager fieldsManager) {
        this.fieldsManager = fieldsManager;
        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    @Override
    public void render(final Context context, final Boolean value, final SafeHtmlBuilder sb) {
        final Style style = fieldsManager.getResources().style();
    }

    interface Template extends SafeHtmlTemplates {
        @Template("<div class=\"{0}\"><div class=\"{1}\">Name:</div><div class=\"{1}\">Sort:</div><div class=\"{1}\">Filter:</div><div class=\"{1}\">Function:</div></div>")
        SafeHtml optionsVisible(String labelsClassName, String labelClassName);
    }
}
