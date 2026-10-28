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

package stroom.entity.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.text.client.Html;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The Info dialog's content: a document's details, tags and audit history.
public class InfoDocument extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Html info;

    private Consumer<NativeEvent> onMouseDown = event -> {
    };

    /// Makes empty content.
    public InfoDocument() {
        initWidget(BINDER.createAndBindUi(this));
        addDomHandler(event -> onMouseDown.accept(event.getNativeEvent()), MouseDownEvent.getType());
    }

    /// @param info The details shown.
    public void setInfo(final SafeHtml info) {
        this.info.setHTML(info);
    }

    /// @param onMouseDown Told of a press on the details, e.g. to copy what was pressed.
    public void setOnMouseDown(final Consumer<NativeEvent> onMouseDown) {
        this.onMouseDown = onMouseDown;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, InfoDocument> {

    }
}
