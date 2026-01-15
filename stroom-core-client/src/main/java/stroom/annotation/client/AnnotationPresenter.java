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

package stroom.annotation.client;

import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.ChangeDescription;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditTabPresenter;
import stroom.entity.client.presenter.DocumentEditTabProvider;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownEditPresenter;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.SvgButton;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import javax.inject.Provider;

public class AnnotationPresenter
        extends DocumentEditTabPresenter<LinkTabPanelView, Annotation> {

    private static final TabData ANNOTATION = new TabDataImpl("Annotation");
    private static final TabData EVENTS = new TabDataImpl("Events");
    private static final TabData LINK_TO = new TabDataImpl("Link To");
    private static final TabData LINK_FROM = new TabDataImpl("Link From");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final ButtonView saveButton;

    private final AnnotationEditPresenter annotationEditPresenter;
    private MarkdownEditPresenter markdownEditPresenter;

    @SuppressWarnings("checkstyle:linelength")
    @Inject
    public AnnotationPresenter(final EventBus eventBus,
                               final LinkTabPanelView view,
                               final AnnotationResourceClient annotationResourceClient,
                               final AnnotationEditPresenter annotationEditPresenter,
                               final AnnotationLinkPresenter linkTo,
                               final AnnotationLinkPresenter linkFrom,
                               final LinkedEventPresenter linkedEventPresenter,
                               final Provider<MarkdownEditPresenter> markdownEditPresenterProvider,
                               final DocumentUserPermissionsTabProvider<Annotation> documentUserPermissionsTabProvider) {
        super(eventBus, view);
        this.annotationEditPresenter = annotationEditPresenter;
        annotationEditPresenter.setParent(this);
        linkedEventPresenter.setParent(this);
        linkTo.setParent(this);
        linkFrom.setParent(this);
        linkFrom.setFrom(true);

        saveButton = SvgButton.create(SvgPresets.SAVE);
        saveButton.setEnabled(false);
        registerHandler(saveButton.addClickHandler(e -> {
            annotationResourceClient.change(
                    new SingleAnnotationChangeRequest(getDocRef(),
                            new ChangeDescription(markdownEditPresenter.getText())),
                    success -> {
                        if (success) {
                            AnnotationChangeEvent.fire(this, getDocRef());
                        }
                    }, this);
            saveButton.setEnabled(false);
        }));

        addTab(ANNOTATION, new DocumentEditTabProvider<>(() -> annotationEditPresenter));
        addTab(EVENTS, new DocumentEditTabProvider<>(() -> linkedEventPresenter));
        addTab(LINK_TO, new DocumentEditTabProvider<>(() -> linkTo));
        addTab(LINK_FROM, new DocumentEditTabProvider<>(() -> linkFrom));
        addTab(DOCUMENTATION, new MarkdownTabProvider<Annotation>(eventBus, () -> {
            if (markdownEditPresenter == null) {
                markdownEditPresenter = markdownEditPresenterProvider.get();
                markdownEditPresenter.setInsertedButtons(Collections.singletonList(saveButton));
                markdownEditPresenter.addDirtyHandler(e -> saveButton.setEnabled(e.isDirty()));
            }
            return markdownEditPresenter;
        }) {
            @Override
            public void onRead(final MarkdownEditPresenter presenter,
                               final DocRef docRef,
                               final Annotation document,
                               final boolean readOnly) {
                presenter.setText(document.getDescription());
                presenter.setReadOnly(readOnly);
            }

            @Override
            public Annotation onWrite(final MarkdownEditPresenter presenter,
                                      final Annotation document) {
//                document.setDescription(presenter.getText());
                return document;
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);

        selectTab(ANNOTATION);
    }

    @Override
    protected ButtonPanel createToolbar() {
        return new ButtonPanel();
    }

    @Override
    public String getType() {
        return Annotation.TYPE;
    }

    @Override
    public String getLabel() {
        final Annotation annotation = getEntity();
        return "#" + annotation.getId() + " " + annotation.getName();
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }

    public void read(final Annotation annotation, final boolean readOnly) {
        read(annotation.asDocRef(), annotation, readOnly);
    }

    public void updateHistory() {
        annotationEditPresenter.updateHistory();
    }

    public void setInitialComment(final String initialComment) {
        annotationEditPresenter.setInitialComment(initialComment);
    }
}
