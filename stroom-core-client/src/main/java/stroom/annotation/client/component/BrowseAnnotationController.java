/*
 * Copyright 2025 Crown Copyright
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

package stroom.annotation.client.component;

import stroom.annotation.client.EditAnnotationEvent;
import stroom.annotation.shared.Annotation;
import stroom.explorer.client.component.FindPanel;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.LayerController;
import stroom.widget.tab.client.TabData;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The Annotations tab: finds the annotations the user may see, filtered as they ask, and opens
/// them.
public class BrowseAnnotationController extends LayerController implements TabData {

    private final FindPanel view;
    private final FindAnnotationListController findAnnotationList;

    /// Made by GIN, with its screen and its list.
    @Inject
    public BrowseAnnotationController(final EventBus eventBus,
                                      final FindPanel view,
                                      final FindAnnotationListController findAnnotationList) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.findAnnotationList = own(findAnnotationList);
        // To browse, users only need view permission.
        findAnnotationList.setPermission(DocumentPermission.VIEW);
        view.setDialogMode(false);
        view.setResultView(findAnnotationList);
        view.setOnFilterChange(this::changeQuickFilter);
        view.setOnFilterKeyDown(this::onFilterKeyDown);
        findAnnotationList.setOnOpen(this::openDocument);
        findAnnotationList.setOnFocusFilter(this::focus);
    }

    /// Opens an annotation found.
    ///
    /// @param match The annotation, or `null` for none.
    public void openDocument(final Annotation match) {
        if (match != null) {
            EditAnnotationEvent.fire(this, match.getId());
        }
    }

    /// Puts the focus on the filter.
    public void focus() {
        view.focus();
    }

    /// Finds the annotations again.
    public void refresh() {
        findAnnotationList.refresh();
    }

    private void changeQuickFilter(final String name) {
        findAnnotationList.setFilter(name);
        findAnnotationList.refresh();
    }

    private void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(findAnnotationList.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findAnnotationList.setKeyboardSelectedRow(0, true);
        }
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.EYE;
    }

    @Override
    public String getLabel() {
        return "Annotations";
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public String getType() {
        return "Annotations";
    }
}
