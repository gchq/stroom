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

package stroom.annotation.client;

import stroom.annotation.client.ChangeStatusPresenter.ChangeStatusView;
import stroom.annotation.client.component.ChooserController;
import stroom.annotation.client.component.OpenChooser;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.FindAnnotationTagCriteria;
import stroom.annotation.shared.MultiAnnotationChangeRequest;
import stroom.annotation.shared.SetTag;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestErrorHandler;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;

public class ChangeStatusPresenter
        extends MyPresenterWidget<ChangeStatusView>
        implements ChangeStatusUiHandlers {

    private final AnnotationResourceClient annotationResourceClient;
    private final Provider<ChooserController<AnnotationTag>> chooserProvider;
    private final OpenChooser openChooser = new OpenChooser();
    private AnnotationTag currentStatus;

    @Inject
    public ChangeStatusPresenter(final EventBus eventBus,
                                 final ChangeStatusView view,
                                 final AnnotationResourceClient annotationResourceClient,
                                 final Provider<ChooserController<AnnotationTag>> chooserProvider) {
        super(eventBus, view);
        this.annotationResourceClient = annotationResourceClient;
        this.chooserProvider = chooserProvider;

        getView().setUiHandlers(this);
    }

    private FindAnnotationTagCriteria createCriteria(final AnnotationTagType annotationTagType,
                                                    final String filter) {
        // The type says what kind of tag is being chosen, so it is a field of the criteria rather
        // than a term; the filter is the user's own text and goes to the server unparsed.
        // See FindAnnotationTagCriteria.
        return new FindAnnotationTagCriteria(annotationTagType, filter);
    }

    public void show(final List<Long> annotationIdList) {
        setStatus(null);

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .modal()
                .popupSize(PopupSize.resizableX(500))
                .caption("Change Status")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final MultiAnnotationChangeRequest request = new MultiAnnotationChangeRequest(annotationIdList,
                                new SetTag(currentStatus));
                        annotationResourceClient.batchChange(request,
                                values -> {
                                    GWT.log("Updated " + values + " annotations");
                                    if (values > 0) {
                                        AnnotationChangeEvent.fire(this, null);
                                    }
                                    e.hide();
                                },
                                RestErrorHandler.forPopup(this, e),
                                this);
                    } else {
                        e.hide();
                    }
                })
                .fire();
    }

    private void changeStatus(final AnnotationTag selected) {
        if (!Objects.equals(currentStatus, selected)) {
            setStatus(selected);
        }
    }

    private void setStatus(final AnnotationTag status) {
        currentStatus = status;
        getView().setStatus(status);
    }

    @Override
    public void showStatusChooser(final Element element) {
        if (openChooser.closeIfOpenedFrom(element)) {
            return;
        }
        final ChooserController<AnnotationTag> chooser = chooserProvider.get();
        chooser.setDataSupplier((filter, consumer) -> {
            final FindAnnotationTagCriteria criteria = createCriteria(AnnotationTagType.STATUS, filter);
            annotationResourceClient.findAnnotationTags(this, criteria, values ->
                            consumer.accept(values.getValues()),
                    new DefaultErrorHandler(this, null), this);
        });
        chooser.setDisplayValueFunction(at -> SafeHtmlUtils.fromString(at.getName()));
        chooser.setAccessibleName("Statuses");
        chooser.setSelected(currentStatus);
        chooser.show(this, element, "Choose Status", selected -> {
            if (!Objects.equals(currentStatus, selected)) {
                changeStatus(selected);
                chooser.hide();
            }
        });
        openChooser.opened(chooser, element);
    }

    public interface ChangeStatusView extends View, Focus, HasUiHandlers<ChangeStatusUiHandlers> {

        void setStatus(AnnotationTag status);
    }
}
