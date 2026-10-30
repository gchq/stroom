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

package stroom.pipeline.structure.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroupResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.client.component.DocSelectionController;
import stroom.meta.shared.MetaResource;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyType;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.pipeline.structure.client.component.NewProperty.ValueEditor;
import stroom.pipeline.structure.client.component.PropertyListController.Source;
import stroom.security.shared.DocumentPermission;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.popup.client.Size;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// The Edit Property dialog ([NewProperty]) of a pipeline element's property, made for one use.
/// The property's type chooses the editor its value is shown in; a change to the value makes the
/// source Local.
public class NewPropertyController extends Controller {

    private static final MetaResource META_RESOURCE = GWT.create(MetaResource.class);
    private static final FsVolumeGroupResource VOLUME_GROUP_RESOURCE = GWT.create(FsVolumeGroupResource.class);

    private final NewProperty view;
    private final RestFactory restFactory;
    private final DocSelectionController documentValue;
    private boolean dirty;

    private PipelineElement pipelineElement;
    private PipelinePropertyType propertyType;
    private PipelineProperty defaultProperty;
    private PipelineProperty inheritedProperty;
    private PipelineProperty localProperty;
    private Source source;

    private boolean streamTypesRequested;
    private String currentStreamType;
    private boolean volumeGroupsRequested;
    private String currentVolumeGroup;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param docSelectionProvider Makes the document picker's controller.
    /// @param restFactory          Fetches the stream types and volume groups.
    @Inject
    public NewPropertyController(final EventBus eventBus,
                                 final NewProperty view,
                                 final Provider<DocSelectionController> docSelectionProvider,
                                 final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        documentValue = make(docSelectionProvider);
        documentValue.attach(view.getDocumentValueBox());
        own(documentValue.addSelectionListener(docRef -> onValueChange()));
        view.setOnValueChange(this::onValueChange);
        view.setOnSourceChange(this::onSourceChange);
    }

    /// Edits a property in a dialog, owned by `opener` until it is closed. Cancel closes it; on
    /// OK `onOk` is given the hide request, and reads the result with [#isDirty()],
    /// [#getSource()] and [#writeValue()], then hides or resets the request.
    ///
    /// @param opener            What shows it.
    /// @param pipelineElement   The element whose property it is.
    /// @param propertyType      The property's type.
    /// @param defaultProperty   The property with its default value.
    /// @param inheritedProperty The property as inherited from a parent pipeline.
    /// @param localProperty     The property as set on this pipeline.
    /// @param source            Where the value comes from now.
    /// @param defaultValue      The default value, as shown.
    /// @param inheritedValue    The inherited value, as shown.
    /// @param inheritedFrom     The pipeline the value is inherited from.
    /// @param onOk              Given the hide request on OK.
    public void edit(final HasControllerNode opener,
                     final PipelineElement pipelineElement,
                     final PipelinePropertyType propertyType,
                     final PipelineProperty defaultProperty,
                     final PipelineProperty inheritedProperty,
                     final PipelineProperty localProperty,
                     final Source source,
                     final String defaultValue,
                     final String inheritedValue,
                     final String inheritedFrom,
                     final Consumer<HidePopupRequestEvent> onOk) {
        this.pipelineElement = pipelineElement;
        this.propertyType = propertyType;
        this.defaultProperty = defaultProperty;
        this.inheritedProperty = inheritedProperty;
        this.localProperty = localProperty;
        this.source = source;

        if (propertyType.canEmbed()) {
            view.addSource(Source.EMBEDDED);
        }
        view.setElement(pipelineElement.getDisplayName());
        view.setName(defaultProperty.getName());
        view.setDescription(propertyType.getDescription());
        view.setDefaultValue(defaultValue);
        view.setInherited(inheritedFrom, inheritedValue);
        view.setSource(source);
        view.showValueEditor(editorFor(propertyType));

        final PopupSize popupSize = PopupSize.builder()
                .width(Size.builder()
                        .initial(600)
                        .min(600)
                        .resizable(true)
                        .build())
                .build();
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Edit Property")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        onOk.accept(e);
                    } else {
                        e.hide();
                    }
                })
                .fire();
        // Once shown, so the stream types or volume groups are fetched with the dialog's monitor
        startEdit(source);
    }

    /// @return Whether the user changed the source or the value.
    public boolean isDirty() {
        return dirty;
    }

    /// @return Where the value comes from.
    public Source getSource() {
        return view.getSource();
    }

    /// @return The value entered, in the editor for the property's type; null for no document.
    public PipelinePropertyValue writeValue() {
        return switch (editorFor(propertyType)) {
            case STREAM_TYPE -> new PipelinePropertyValue(view.getStreamTypeValue());
            case VOLUME_GROUP -> new PipelinePropertyValue(view.getVolumeGroupValue());
            case BOOLEAN -> new PipelinePropertyValue(Boolean.valueOf(view.getBooleanValue()));
            case NUMBER -> "int".equals(propertyType.getType())
                    ? new PipelinePropertyValue(view.getNumberValue())
                    : new PipelinePropertyValue((long) view.getNumberValue());
            case TEXT -> new PipelinePropertyValue(view.getTextValue());
            case DOCUMENT -> {
                final DocRef docRef = documentValue.getSelectedEntityReference();
                yield docRef == null
                        ? null
                        : new PipelinePropertyValue(docRef);
            }
        };
    }

    /// @param propertyType A property's type.
    /// @return The editor its value is shown in.
    static ValueEditor editorFor(final PipelinePropertyType propertyType) {
        if ("streamType".equals(propertyType.getName())) {
            return ValueEditor.STREAM_TYPE;
        } else if ("volumeGroup".equals(propertyType.getName())) {
            return ValueEditor.VOLUME_GROUP;
        } else if ("boolean".equals(propertyType.getType())) {
            return ValueEditor.BOOLEAN;
        } else if ("int".equals(propertyType.getType()) || "long".equals(propertyType.getType())) {
            return ValueEditor.NUMBER;
        } else if ("String".equals(propertyType.getType())) {
            return ValueEditor.TEXT;
        }
        return ValueEditor.DOCUMENT;
    }

    private void onSourceChange(final Source source) {
        if (!this.source.equals(source)) {
            this.source = source;
            dirty = true;
            startEdit(source);
        }
    }

    // A change to the value makes the source Local
    private void onValueChange() {
        dirty = true;
        source = Source.LOCAL;
        view.setSource(Source.LOCAL);
    }

    private void startEdit(final Source source) {
        switch (source) {
            case DEFAULT -> startEdit(defaultProperty);
            case INHERIT -> startEdit(inheritedProperty);
            case LOCAL -> startEdit(localProperty);
            case EMBEDDED -> showEmbeddedProperty();
        }
    }

    // An embedded document is named for the element, and its name can't be changed
    private void showEmbeddedProperty() {
        view.showValueEditor(ValueEditor.TEXT);
        view.setTextValue("EMBEDDED " + propertyType.getDocRefTypes()[0] + " (" + pipelineElement.getId() + ")",
                false);
    }

    private void startEdit(final PipelineProperty property) {
        final ValueEditor editor = editorFor(propertyType);
        view.showValueEditor(editor);
        final PipelinePropertyValue value = property.getValue();
        switch (editor) {
            case STREAM_TYPE -> {
                currentStreamType = value != null && value.getString() != null
                        ? value.toString()
                        : null;
                requestStreamTypes();
                view.setStreamTypeValue(currentStreamType);
            }
            case VOLUME_GROUP -> {
                currentVolumeGroup = value != null && value.getString() != null
                        ? value.toString()
                        : null;
                requestVolumeGroups();
                view.setVolumeGroupValue(currentVolumeGroup);
            }
            case BOOLEAN -> view.setBooleanValue(value != null && value.getBoolean() != null
                    ? value.getBoolean().toString().toLowerCase()
                    : "false");
            case NUMBER -> view.setNumberValue(numberOf(value));
            case TEXT -> view.setTextValue(value != null && value.getString() != null
                    ? value.getString()
                    : "", true);
            case DOCUMENT -> {
                documentValue.setIncludedTypes(propertyType.getDocRefTypes());
                documentValue.setRequiredPermissions(DocumentPermission.USE);
                try {
                    documentValue.setSelectedEntityReference(value != null
                            ? value.getEntity()
                            : null, true);
                } catch (final RuntimeException e) {
                    AlertEvent.fireError(this, e.getMessage(), null);
                }
            }
        }
    }

    private Long numberOf(final PipelinePropertyValue value) {
        if (value != null) {
            if ("int".equals(propertyType.getType()) && value.getInteger() != null) {
                return Long.valueOf(value.getInteger());
            }
            if ("long".equals(propertyType.getType()) && value.getLong() != null) {
                return value.getLong();
            }
        }
        return 0L;
    }

    // The stream types are fetched once; the value is chosen again once they are there
    private void requestStreamTypes() {
        if (!streamTypesRequested) {
            streamTypesRequested = true;
            restFactory
                    .create(META_RESOURCE)
                    .method(MetaResource::getTypes)
                    .onSuccess(result -> {
                        if (result != null) {
                            view.setStreamTypes(result);
                        }
                        if (currentStreamType != null) {
                            view.setStreamTypeValue(currentStreamType);
                        }
                    })
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

    // The volume groups are fetched once; the value is chosen again once they are there
    private void requestVolumeGroups() {
        if (!volumeGroupsRequested) {
            volumeGroupsRequested = true;
            restFactory
                    .create(VOLUME_GROUP_RESOURCE)
                    .method(res -> res.find(new ExpressionCriteria()))
                    .onSuccess(result -> {
                        final List<String> names = result != null && result.getValues() != null
                                ? result.getValues().stream().map(FsVolumeGroup::getName).collect(Collectors.toList())
                                : List.of();
                        view.setVolumeGroups(names);
                        if (currentVolumeGroup != null) {
                            view.setVolumeGroupValue(currentVolumeGroup);
                        }
                    })
                    .owner(this)
                    .taskMonitorFactory(taskMonitor())
                    .exec();
        }
    }

}
