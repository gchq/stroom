/*
 * Copyright 2019 Crown Copyright
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

import stroom.annotation.client.AnnotationChangeEvent;
import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.FindAnnotationTagCriteria;
import stroom.annotation.shared.MultiAnnotationChangeRequest;
import stroom.annotation.shared.SetTag;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;

/// Change Status ([ChangeStatus]): sets one status on several annotations, in a dialog made
/// for one use.
public class ChangeStatusController extends Controller {

    private final ChangeStatus view;
    private final AnnotationResourceClient annotationResourceClient;
    private final Provider<ChooserController<AnnotationTag>> chooserProvider;
    private final OpenChooser openChooser = new OpenChooser();
    private AnnotationTag currentStatus;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The content.
    /// @param annotationResourceClient Finds the statuses and changes the annotations.
    /// @param chooserProvider          Makes the status chooser.
    @Inject
    public ChangeStatusController(final EventBus eventBus,
                                  final ChangeStatus view,
                                  final AnnotationResourceClient annotationResourceClient,
                                  final Provider<ChooserController<AnnotationTag>> chooserProvider) {
        super(eventBus);
        this.view = view;
        this.annotationResourceClient = annotationResourceClient;
        this.chooserProvider = chooserProvider;
        initWidget(view);
        view.setOnChooseStatus(this::showStatusChooser);
    }

    /// Shows the dialog, owned by `opener` until it is closed; OK sets the status chosen on the
    /// annotations.
    ///
    /// @param opener           What shows it.
    /// @param annotationIdList The annotations.
    public void show(final HasControllerNode opener, final List<Long> annotationIdList) {
        setStatus(null);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizableX(500))
                .caption("Change Status")
                .onHideRequest(e -> {
                    if (e.isOk() && currentStatus == null) {
                        // The server can't set no status, so say what is missing and keep the dialog
                        view.setStatusMissing("Choose a status to set");
                        e.reset();
                    } else if (e.isOk()) {
                        final MultiAnnotationChangeRequest request = new MultiAnnotationChangeRequest(
                                annotationIdList,
                                new SetTag(currentStatus));
                        annotationResourceClient.batchChange(this, request,
                                values -> {
                                    GWT.log("Updated " + values + " annotations");
                                    if (values > 0) {
                                        AnnotationChangeEvent.fire(this, null);
                                    }
                                    e.hide();
                                },
                                RestErrorHandler.forPopup(this, e),
                                taskMonitor());
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void setStatus(final AnnotationTag status) {
        currentStatus = status;
        view.setStatus(status);
    }

    private void showStatusChooser(final Element element) {
        if (openChooser.closeIfOpenedFrom(element)) {
            return;
        }
        final ChooserController<AnnotationTag> chooser = chooserProvider.get();
        chooser.setDataSupplier((filter, consumer) -> {
            // The type says what kind of tag is being chosen, so it is a field of the criteria
            // rather than a term; the filter is the user's own text and goes to the server
            // unparsed. See FindAnnotationTagCriteria.
            final FindAnnotationTagCriteria criteria = new FindAnnotationTagCriteria(AnnotationTagType.STATUS,
                    filter);
            annotationResourceClient.findAnnotationTags(this, criteria, values ->
                            consumer.accept(values.getValues()),
                    new DefaultErrorHandler(this, null), taskMonitor());
        });
        chooser.setDisplayValueFunction(at -> SafeHtmlUtils.fromString(at.getName()));
        chooser.setAccessibleName("Statuses");
        chooser.setSelected(currentStatus);
        chooser.show(this, element, "Choose Status", selected -> {
            if (!Objects.equals(currentStatus, selected)) {
                setStatus(selected);
                chooser.hide();
            }
        });
        openChooser.opened(chooser, element);
    }

}
