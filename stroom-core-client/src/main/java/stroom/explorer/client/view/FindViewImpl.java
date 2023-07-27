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

package stroom.explorer.client.view;

import stroom.explorer.client.presenter.FindPresenter;
import stroom.explorer.client.presenter.FindUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgToggleButton;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class FindViewImpl extends ViewWithUiHandlers<FindUiHandlers>
        implements FindPresenter.FindView {

    private final Widget widget;

    @UiField
    TextBox pattern;
    @UiField
    SimplePanel resultContainer;
    @UiField
    InlineSvgToggleButton toggleMatchCase;
    @UiField
    InlineSvgToggleButton toggleRegex;

    @Inject
    public FindViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        toggleMatchCase.setSvg(SvgImage.CASE_SENSITIVE);
        toggleMatchCase.setTitle("Match case");
        toggleMatchCase.setEnabled(true);

        toggleRegex.setSvg(SvgImage.REGEX);
        toggleRegex.setTitle("Regex");
        toggleRegex.setEnabled(true);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getPattern() {
        return pattern.getValue();
    }

    @Override
    public void setResultView(final View view) {
        resultContainer.setWidget(view.asWidget());
    }

    @Override
    public void focus() {
        pattern.setFocus(true);
    }

    @UiHandler("pattern")
    void onPatternChange(final KeyUpEvent e) {
        getUiHandlers().changePattern(pattern.getText(), toggleMatchCase.getState(), toggleRegex.getState());
    }

    @UiHandler("toggleMatchCase")
    void onToggleMatchCase(final ClickEvent e) {
        getUiHandlers().changePattern(pattern.getText(), toggleMatchCase.getState(), toggleRegex.getState());
    }

    @UiHandler("toggleRegex")
    void onToggleRegex(final ClickEvent e) {
        getUiHandlers().changePattern(pattern.getText(), toggleMatchCase.getState(), toggleRegex.getState());
    }

    public interface Binder extends UiBinder<Widget, FindViewImpl> {

    }
}
