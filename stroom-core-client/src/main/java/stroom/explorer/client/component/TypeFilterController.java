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

package stroom.explorer.client.component;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.explorer.client.DocumentTypeSelectionModel;
import stroom.explorer.shared.DocumentTypes;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.Listeners;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// The explorer's type filter ([TypeFilter]), shown in a popup beside its button: which document
/// types are shown. Its owner keeps one, shows it again and again, and listens for changes.
public class TypeFilterController extends Controller implements DocumentTypeSelectionModel {

    private static final String SELECT_ALL_OR_NONE_TEXT = "All / None";
    private static final DocumentType SELECT_ALL_OR_NONE_DOCUMENT_TYPE = new DocumentType(
            DocumentTypeGroup.SYSTEM,
            SELECT_ALL_OR_NONE_TEXT,
            SELECT_ALL_OR_NONE_TEXT,
            SvgImage.DOCUMENT_SELECT_ALL_OR_NONE);

    private final TypeFilter view;
    private final Set<String> selected = new HashSet<>();
    private final Listeners<Optional<Set<String>>> selectionListeners = new Listeners<>();
    private List<DocumentType> visibleTypes;
    private Consumer<Boolean> filterStateConsumer;
    private Runnable onHidden = () -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public TypeFilterController(final EventBus eventBus, final TypeFilter view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setTicks(this, SELECT_ALL_OR_NONE_DOCUMENT_TYPE);
        view.setOnToggle(this::toggle);
        view.setOnToggleAll(() -> {
            toggleSelectAll();
            refreshView();
            fireSelection();
        });
    }

    /// Shows the filter in a popup beside an element, its first type focused. Showing it while it
    /// is shown hides it (the popup manager does).
    ///
    /// @param element             The element it is shown beside, e.g. its button.
    /// @param filterStateConsumer Told whether a filter is active, at each change and once hidden.
    public void show(final Element element, final Consumer<Boolean> filterStateConsumer) {
        this.filterStateConsumer = filterStateConsumer;
        ControllerDialog.show(this)
                .popupType(PopupType.POPUP)
                .caption("Filter Types")
                .popupPosition(new PopupPosition(new Rect(element).grow(3), PopupLocation.RIGHT))
                .addAutoHidePartner(element)
                .onShow(e -> view.focus())
                .onHide(event -> {
                    if (filterStateConsumer != null) {
                        filterStateConsumer.accept(hasActiveFilter());
                    }
                    onHidden.run();
                })
                .fire();
    }

    /// Hides the popup, as a click outside it does.
    public void hide() {
        ControllerDialog.hide(this, true, false);
    }

    /// @param onHidden Run each time the popup is hidden.
    public void setOnHidden(final Runnable onHidden) {
        this.onHidden = onHidden;
    }

    /// Offers these types, every one ticked.
    ///
    /// @param documentTypes The types; their visible ones are offered, by name.
    public void setDocumentTypes(final DocumentTypes documentTypes) {
        visibleTypes = documentTypes.getVisibleTypes()
                .stream()
                .sorted(Comparator.comparing(DocumentType::getDisplayType))
                .collect(Collectors.toList());
        selectAll();
        refreshView();
    }

    /// Ticks or unticks a type, as the user does.
    ///
    /// @param type The type, or All / None; null does nothing.
    public void toggle(final DocumentType type) {
        if (type == null) {
            return;
        }
        if (type.equals(SELECT_ALL_OR_NONE_DOCUMENT_TYPE)) {
            toggleSelectAll();
        } else if (!selected.remove(type.getType())) {
            selected.add(type.getType());
        }
        if (filterStateConsumer != null) {
            filterStateConsumer.accept(hasActiveFilter());
        }
        refreshView();
        fireSelection();
    }

    /// @return The types to include, or empty if the filter isn't active (every visible type is
    ///         ticked, so the back end needn't filter on them all).
    public Optional<Set<String>> getIncludedTypes() {
        if (visibleTypes != null) {
            final Set<String> visibleTypeNames = visibleTypes.stream()
                    .map(DocumentType::getType)
                    .collect(Collectors.toSet());
            if (selected.containsAll(visibleTypeNames)) {
                // The visible types are those the user may read, so filtering on all is pointless
                return Optional.empty();
            }
        }
        return Optional.of(selected);
    }

    /// @return Whether some types aren't ticked.
    public boolean hasActiveFilter() {
        return getIncludedTypes().isPresent();
    }

    /// Listens for the user changing the types shown, until the registration is removed.
    ///
    /// @param listener Given the types to include, as [#getIncludedTypes()].
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addSelectionListener(final Consumer<Optional<Set<String>>> listener) {
        return selectionListeners.add(listener);
    }

    @Override
    public TickBoxState getState(final DocumentType type) {
        if (type.equals(SELECT_ALL_OR_NONE_DOCUMENT_TYPE)) {
            if (selected.isEmpty()) {
                return TickBoxState.UNTICK;
            } else if (selected.size() == visibleTypes.size()) {
                return TickBoxState.TICK;
            } else {
                return TickBoxState.HALF_TICK;
            }
        } else if (selected.contains(type.getType())) {
            return TickBoxState.TICK;
        }
        return TickBoxState.UNTICK;
    }

    private void fireSelection() {
        selectionListeners.fire(getIncludedTypes());
    }

    private void selectAll() {
        for (final DocumentType documentType : visibleTypes) {
            selected.add(documentType.getType());
        }
    }

    private void toggleSelectAll() {
        if (selected.size() == visibleTypes.size()) {
            selected.clear();
        } else {
            selectAll();
        }
    }

    // All / None, then the types
    private void refreshView() {
        final List<DocumentType> selectableTypes = new ArrayList<>(visibleTypes);
        selectableTypes.add(0, SELECT_ALL_OR_NONE_DOCUMENT_TYPE);
        view.setTypes(selectableTypes);
    }
}
