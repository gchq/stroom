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

package stroom.dashboard.client.input;

import stroom.dashboard.client.input.ListInputPresenter.ListInputView;
import stroom.dashboard.client.input.ListInputPresenter.WordItem;
import stroom.docref.DocRef;
import stroom.item.client.SelectionBox;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public class ListInputViewImpl extends ViewWithUiHandlers<ListInputUiHandlers> implements ListInputView {

    private static final SafeHtml DICT_ICON_HTML = SvgImageUtil.toSafeHtml(
            "Source Dictionary",
            SvgImage.DOCUMENT_DICTIONARY,
            "explorerCell-icon");

    private final Widget widget;

    @UiField
    SelectionBox<WordItem> valueSelectionBox;

    private boolean allowTextEntry;

    @Inject
    public ListInputViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        valueSelectionBox.setRenderFunction(wordItem -> {
            final String word = wordItem.getWord();

            return wordItem.getSourceDocRef()
                    .map(docRef ->
                            NullSafe.getOrElseGet(docRef,
                                    DocRef::getName,
                                    docRef::getUuid))
                    .map(sourceVal -> {
                        return HtmlBuilder.builder()
                                .div(containerBuilder -> containerBuilder
                                                .div(word, cssClass("-word"))
                                                .div(sourceBuilder -> {
                                                    sourceBuilder
                                                            .div(sourceVal, cssClass("-source-text"))
                                                            .append(DICT_ICON_HTML);
                                                }, cssClass("-source")),
                                        cssClass("-container"))
                                .toSafeHtml();
                    })
                    .orElseGet(() ->
                            SafeHtmlUtils.fromString(word));
        });
    }

    private Attribute cssClass(final String suffix) {
        return Attribute.className("listInputItem" + suffix);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setValues(final List<WordItem> values) {
        final WordItem selected = valueSelectionBox.getValue();
        valueSelectionBox.clear();
        if (values != null) {
            valueSelectionBox.addItem(WordItem.EMPTY);
            valueSelectionBox.addItems(values);
        }
        valueSelectionBox.setValue(selected);
    }

    @Override
    public void setSelectedValue(final WordItem selected) {
        this.valueSelectionBox.setValue(selected);
    }

    @Override
    public String getSelectedValue() {
        return valueSelectionBox.getText();
    }

    @Override
    public void setAllowTextEntry(final boolean allowTextEntry) {
        this.allowTextEntry = allowTextEntry;
        this.valueSelectionBox.setAllowTextEntry(allowTextEntry);
    }

    @UiHandler("valueSelectionBox")
    public void onSelectionChange(final ValueChangeEvent<WordItem> event) {
        if (getUiHandlers() != null) {
            if (allowTextEntry) {
                getUiHandlers().onValueChanged(WordItem.simpleWord(valueSelectionBox.getText()));
            } else {
                getUiHandlers().onValueChanged(valueSelectionBox.getValue());
            }
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ListInputViewImpl> {

    }
}
