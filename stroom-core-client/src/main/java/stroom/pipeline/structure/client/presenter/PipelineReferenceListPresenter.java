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

package stroom.pipeline.structure.client.presenter;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.alert.client.event.AlertEvent;
import stroom.data.grid.client.*;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.*;
import stroom.widget.button.client.GlyphButtonView;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.util.client.MySingleSelectionModel;

import java.util.*;

public class PipelineReferenceListPresenter extends MyPresenterWidget<DataGridView<PipelineReference>>
        implements HasDirtyHandlers {
    private final MySingleSelectionModel<PipelineReference> selectionModel;

    private enum State {
        INHERITED, ADDED, REMOVED
    }

    private static final SafeHtml ADDED = SafeHtmlUtils.fromSafeConstant("<div style=\"font-weight:bold\">");
    private static final SafeHtml REMOVED = SafeHtmlUtils
            .fromSafeConstant("<div style=\"font-weight:bold;text-decoration:line-through\">");
    private static final SafeHtml INHERITED = SafeHtmlUtils.fromSafeConstant("<div style=\"color:black\">");
    private static final SafeHtml END = SafeHtmlUtils.fromSafeConstant("</div>");

    private final GlyphButtonView addButton;
    private final GlyphButtonView editButton;
    private final GlyphButtonView removeButton;

    private Map<PipelineElementType, Map<String, PipelinePropertyType>> allPropertyTypes;
    private PipelineEntity pipeline;
    private PipelineModel pipelineModel;

    private PipelineElement currentElement;
    private final Map<PipelineReference, State> referenceStateMap = new HashMap<PipelineReference, State>();
    private final List<PipelineReference> references = new ArrayList<PipelineReference>();

    private final Provider<NewPipelineReferencePresenter> newPipelineReferencePresenter;
    private PipelinePropertyType propertyType;

    @Inject
    public PipelineReferenceListPresenter(final EventBus eventBus,
                                          final Provider<NewPipelineReferencePresenter> newPipelineReferencePresenter) {
        super(eventBus, new DataGridViewImpl<PipelineReference>(true));
        this.newPipelineReferencePresenter = newPipelineReferencePresenter;

        selectionModel = new MySingleSelectionModel<PipelineReference>();
        getView().setSelectionModel(selectionModel);

        addButton = getView().addButton(GlyphIcons.NEW_ITEM);
        addButton.setTitle("New Reference");
        addButton.setEnabled(false);

        editButton = getView().addButton(GlyphIcons.EDIT);
        editButton.setTitle("Edit Reference");
        editButton.setEnabled(false);

        removeButton = getView().addButton(GlyphIcons.REMOVE);
        removeButton.setTitle("Remove Refefence");
        removeButton.setEnabled(false);

        addColumns();
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(final SelectionChangeEvent event) {
                enableButtons();
            }
        }));
        registerHandler(getView().addDoubleClickHandler(new DoubleClickEvent.Handler() {
            @Override
            public void onDoubleClick(final DoubleClickEvent event) {
                onEdit(selectionModel.getSelectedObject());
            }
        }));
        registerHandler(addButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    onAdd(event);
                }
            }
        }));
        registerHandler(editButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    onEdit(selectionModel.getSelectedObject());
                }
            }
        }));
        registerHandler(removeButton.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
                if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
                    onRemove();
                }
            }
        }));
    }

    private void addColumns() {
        addPipelineColumn();
        addFeedColumn();
        addStreamTypeColumn();
        addInheritedFromColumn();

        addEndColumn();
    }

    private void addPipelineColumn() {
        // Pipeline.
        getView().addResizableColumn(new Column<PipelineReference, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineReference pipelineReference) {
                if (pipelineReference.getPipeline() == null) {
                    return null;
                }
                return getSafeHtmlWithState(pipelineReference, pipelineReference.getPipeline().getName());
            }
        }, "Pipeline", 200);
    }

    private void addFeedColumn() {
        // Feed.
        getView().addResizableColumn(new Column<PipelineReference, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineReference pipelineReference) {
                if (pipelineReference.getFeed() == null) {
                    return null;
                }
                return getSafeHtmlWithState(pipelineReference, pipelineReference.getFeed().getName());
            }
        }, "Feed", 200);
    }

    private void addStreamTypeColumn() {
        // Stream type.
        getView().addResizableColumn(new Column<PipelineReference, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final PipelineReference pipelineReference) {
                if (pipelineReference.getStreamType() == null) {
                    return null;
                }
                return getSafeHtmlWithState(pipelineReference, pipelineReference.getStreamType());
            }
        }, "Stream Type", 200);
    }

    private void addInheritedFromColumn() {
        // Default Value.
        getView().addResizableColumn(new Column<PipelineReference, String>(new TextCell()) {
            @Override
            public String getValue(final PipelineReference pipelineReference) {
                if (pipelineReference.getSource().getPipeline() == null
                        || pipelineReference.getSource().getPipeline().equals(pipeline)) {
                    return null;
                }
                return pipelineReference.getSource().getPipeline().getName();
            }
        }, "Inherited From", 100);
    }

    private void addEndColumn() {
        getView().addEndColumn(new EndColumn<PipelineReference>());
    }

    private SafeHtml getSafeHtmlWithState(final PipelineReference pipelineReference, final String string) {
        if (string == null) {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }

        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        final State state = referenceStateMap.get(pipelineReference);
        switch (state) {
            case ADDED:
                builder.append(ADDED);
                break;
            case REMOVED:
                builder.append(REMOVED);
                break;
            case INHERITED:
                builder.append(INHERITED);
                break;
        }

        builder.appendEscaped(string);
        builder.append(END);

        return builder.toSafeHtml();
    }

    public void setPipeline(final PipelineEntity pipeline) {
        this.pipeline = pipeline;
    }

    public void setPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
    }

    public void setCurrentElement(final PipelineElement currentElement) {
        this.currentElement = currentElement;

        // Discover the reference property type.
        this.propertyType = null;
        if (currentElement != null && allPropertyTypes != null) {
            final Map<String, PipelinePropertyType> propertyTypes = allPropertyTypes
                    .get(currentElement.getElementType());
            if (propertyTypes != null) {
                for (final PipelinePropertyType propertyType : propertyTypes.values()) {
                    if (propertyType.isPipelineReference()) {
                        this.propertyType = propertyType;
                    }
                }
            }
        }

        refresh();
    }

    public void setPropertyTypes(final Map<PipelineElementType, Map<String, PipelinePropertyType>> propertyTypes) {
        this.allPropertyTypes = propertyTypes;
    }

    private void onAdd(final ClickEvent event) {
        if (currentElement != null) {
            final PipelineReference pipelineReference = new PipelineReference(currentElement.getId(),
                    propertyType.getName(), null, null, null);
            pipelineReference.setPropertyType(propertyType);
            pipelineReference.setSource(new SourcePipeline(pipeline));
            showEditor(pipelineReference, true);
        }
    }

    private void onEdit(final PipelineReference pipelineReference) {
        if (pipelineReference != null) {
            // Only allow edit of added references.
            final State state = referenceStateMap.get(pipelineReference);
            if (State.ADDED.equals(state)) {
                showEditor(pipelineReference, false);
            }
        }
    }

    private void showEditor(final PipelineReference pipelineReference, final boolean isNew) {
        if (pipelineReference != null) {
            final List<PipelineReference> added = pipelineModel.getPipelineData().getAddedPipelineReferences();
            if (added.contains(pipelineReference)) {
                added.remove(pipelineReference);
            }

            final NewPipelineReferencePresenter editor = newPipelineReferencePresenter.get();
            editor.read(pipelineReference);

            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        editor.write(pipelineReference);

                        if (pipelineReference.getPipeline() == null) {
                            AlertEvent.fireError(PipelineReferenceListPresenter.this,
                                    "You must specify a pipeline to use.", null);
                        } else if (pipelineReference.getFeed() == null) {
                            AlertEvent.fireError(PipelineReferenceListPresenter.this, "You must specify a feed to use.",
                                    null);
                        } else if (pipelineReference.getStreamType() == null) {
                            AlertEvent.fireError(PipelineReferenceListPresenter.this,
                                    "You must specify a stream type to use.", null);
                        } else {
                            if (!added.contains(pipelineReference)) {
                                added.add(pipelineReference);
                            }

                            setDirty(isNew || editor.isDirty());
                            refresh();
                            HidePopupEvent.fire(PipelineReferenceListPresenter.this, editor);
                        }
                    } else {
                        // User has cancelled edit so add the reference back to
                        // the list if this was an existing reference
                        if (!isNew) {
                            if (!added.contains(pipelineReference)) {
                                added.add(pipelineReference);
                            }
                        }

                        HidePopupEvent.fire(PipelineReferenceListPresenter.this, editor);
                    }
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    // Do nothing.
                }
            };

            final PopupSize popupSize = new PopupSize(300, 153, 300, 153, 2000, 153, true);
            if (isNew) {
                ShowPopupEvent.fire(this, editor, PopupType.OK_CANCEL_DIALOG, popupSize, "New Pipeline Reference",
                        popupUiHandlers);
            } else {
                ShowPopupEvent.fire(this, editor, PopupType.OK_CANCEL_DIALOG, popupSize, "Edit Pipeline Reference",
                        popupUiHandlers);
            }
        }
    }

    private void onRemove() {
        final PipelineReference selected = selectionModel.getSelectedObject();
        if (selected != null) {
            if (pipelineModel.getPipelineData().getAddedPipelineReferences().contains(selected)) {
                pipelineModel.getPipelineData().getAddedPipelineReferences().remove(selected);
                pipelineModel.getPipelineData().getRemovedPipelineReferences().remove(selected);

            } else {
                if (pipelineModel.getPipelineData().getRemovedPipelineReferences().contains(selected)) {
                    pipelineModel.getPipelineData().getRemovedPipelineReferences().remove(selected);
                } else {
                    pipelineModel.getPipelineData().getRemovedPipelineReferences().add(selected);
                }
            }

            setDirty(true);
            refresh();
        }
    }

    private void refresh() {
        referenceStateMap.clear();
        references.clear();
        selectionModel.clear();

        if (currentElement != null) {
            final String id = currentElement.getId();
            if (id != null) {
                final Map<String, List<PipelineReference>> baseReferences = pipelineModel.getBaseData()
                        .getPipelineReferences().get(id);
                if (baseReferences != null) {
                    for (final List<PipelineReference> list : baseReferences.values()) {
                        for (final PipelineReference reference : list) {
                            referenceStateMap.put(reference, State.INHERITED);
                        }
                    }
                }
                for (final PipelineReference reference : pipelineModel.getPipelineData().getAddedPipelineReferences()) {
                    if (id.equals(reference.getElement())) {
                        referenceStateMap.put(reference, State.ADDED);
                    }
                }
                for (final PipelineReference reference : pipelineModel.getPipelineData()
                        .getRemovedPipelineReferences()) {
                    if (id.equals(reference.getElement())) {
                        referenceStateMap.put(reference, State.REMOVED);
                    }
                }

                references.addAll(referenceStateMap.keySet());
                Collections.sort(this.references);
            }
        }

        getView().setRowData(0, references);
        getView().setRowCount(references.size());

        enableButtons();
    }

    protected void enableButtons() {
        addButton.setEnabled(propertyType != null);

        final PipelineReference selected = selectionModel.getSelectedObject();
        final State state = referenceStateMap.get(selected);

        editButton.setEnabled(State.ADDED.equals(state));
        removeButton.setEnabled(selected != null);
    }

    protected void setDirty(final boolean dirty) {
        if (dirty) {
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
