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

package stroom.xmleditor.client.presenter;

import java.util.List;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.HasMouseDownHandlers;
import com.google.gwt.user.client.ui.HasText;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import stroom.util.shared.Highlight;
import stroom.util.shared.Indicators;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;
import stroom.xmleditor.client.event.HasFormatHandlers;

public interface XMLEditorView extends View, HasKeyDownHandlers, HasFormatHandlers, HasText, HasMouseDownHandlers,
        HasContextMenuHandlers, HasUiHandlers<XMLEditorUiHandlers> {
    void setText(String content, int startLineNo, boolean format, List<Highlight> highlights, Indicators indicators,
            final boolean controlsVisible);

    void setHTML(String html, List<Highlight> highlights, boolean controlsVisible);

    void refresh(boolean format);

    void formatXML();

    Option getStylesOption();

    Option getLineNumbersOption();

    Option getIndicatorsOption();

    Indicators getIndicators();

    void setIndicators(Indicators indicators);

    void showFilterButton(boolean show);

    void setFilterActive(boolean active);

    void setControlsHeight(int controlsHeight);
}
