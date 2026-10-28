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

package stroom.receive.content.client.component;

import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The Content Templates screen: the templates, and the selected one's description and expression.
public class ContentTemplateTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel table;
    @UiField
    FormGroup descriptionFormGroup;
    @UiField
    HTML description;
    @UiField
    FormGroup expressionFormGroup;
    @UiField
    SimplePanel expression;

    /// Makes the screen, with no content until it is given it.
    public ContentTemplateTab() {
        initWidget(BINDER.createAndBindUi(this));
        descriptionFormGroup.setVisible(false);
        expressionFormGroup.setVisible(false);
    }

    /// Shows the list of templates.
    ///
    /// @param view Its view.
    public void setTableView(final IsWidget view) {
        this.table.setWidget(view.asWidget());
    }

    /// Shows the selected template's description, or hides it.
    ///
    /// @param description The description, or null or blank for none.
    public void setDescription(final String description) {
        if (NullSafe.isBlankString(description)) {
            this.description.setHTML(SafeHtmlUtils.EMPTY_SAFE_HTML);
            this.descriptionFormGroup.setVisible(false);
        } else {
            this.description.setHTML(SafeHtmlUtil.toParagraphs(description));
            this.descriptionFormGroup.setVisible(true);
        }
    }

    /// Shows the selected template's expression, or hides it.
    ///
    /// @param view The expression's view, or null for none.
    public void setExpressionView(final IsWidget view) {
        if (view != null) {
            this.expressionFormGroup.setVisible(true);
            this.expression.setWidget(view.asWidget());
        } else {
            this.expressionFormGroup.setVisible(false);
            this.expression.setWidget(null);
        }
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, ContentTemplateTab> {

    }
}
