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

package stroom.hyperlink.client;

import stroom.alert.client.event.ConfirmEvent;
import stroom.annotation.client.CreateAnnotationEvent;
import stroom.annotation.client.EditAnnotationEvent;
import stroom.annotation.shared.EventId;
import stroom.core.client.ContentManager;
import stroom.core.client.event.CloseContentEvent;
import stroom.data.client.presenter.DataViewType;
import stroom.data.client.presenter.DisplayMode;
import stroom.data.client.presenter.ShowDataEvent;
import stroom.document.client.event.CloseSelectedDocumentEvent;
import stroom.iframe.client.presenter.IFrameContentPresenter;
import stroom.iframe.client.presenter.IFramePresenter;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TextRange;
import stroom.util.shared.UserRef;
import stroom.widget.popup.client.event.RenamePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Window;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HandlerContainerImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

@Singleton
public class HyperlinkEventHandlerImpl extends HandlerContainerImpl implements HyperlinkEvent.Handler, HasHandlers {

    private final EventBus eventBus;
    private final Provider<IFrameContentPresenter> iFrameContentPresenterProvider;
    private final Provider<IFramePresenter> iFramePresenterProvider;
    private final ContentManager contentManager;

    @Inject
    public HyperlinkEventHandlerImpl(final EventBus eventBus,
                                     final Provider<IFramePresenter> iFramePresenterProvider,
                                     final Provider<IFrameContentPresenter> iFrameContentPresenterProvider,
                                     final ContentManager contentManager) {
        this.eventBus = eventBus;
        this.iFramePresenterProvider = iFramePresenterProvider;
        this.iFrameContentPresenterProvider = iFrameContentPresenterProvider;
        this.contentManager = contentManager;

        registerHandler(eventBus.addHandler(HyperlinkEvent.getType(), this));
    }

    private static native void nativeConsoleLog(String s) /*-{
        $wnd.console.log(s);
    }-*/;

    @Override
    public void onLink(final HyperlinkEvent event) {
        try {
            final Hyperlink hyperlink = event.getHyperlink();
            nativeConsoleLog("HyperlinkEvent: " + hyperlink.getHref());

            final String href = hyperlink.getHref();
            String type = hyperlink.getType();
            String customTitle = null;
            if (type != null) {
                final int index = type.indexOf("|");
                if (index != -1) {
                    customTitle = type.substring(index + 1);
                    type = type.substring(0, index);
                }
            }

            HyperlinkType hyperlinkType = null;
            if (type != null) {
                try {
                    hyperlinkType = HyperlinkType.valueOf(type.toUpperCase());
                } catch (final RuntimeException e) {
                    GWT.log("Could not parse open type value of " + type);
                }
            }

            if (hyperlinkType != null) {
                switch (hyperlinkType) {
                    case DASHBOARD: {
                        final HyperlinkTargetType target = hyperlink.getTargetType();
                        if (HyperlinkTargetType.SELF.equals(target)) {
                            CloseSelectedDocumentEvent.fire(this,
                                    () -> ShowDashboardEvent.fire(this, event.getContext(), href),
                                    false);
                        } else {
                            ShowDashboardEvent.fire(this, event.getContext(), href);
                        }
                        break;
                    }
                    case TAB: {
                        openTab(hyperlink, customTitle);
                        break;
                    }
                    case DIALOG: {
                        openDialog(hyperlink, customTitle);
                        break;
                    }
                    case BROWSER: {
                        Window.open(href, "_blank", "");
                        break;
                    }
                    case STEPPING: {
                        openStepping(href);
                        break;
                    }
                    case DATA: {
                        openData(href);
                        break;
                    }
                    case ANNOTATION: {
                        openAnnotation(href);
                        break;
                    }
                    default:
                        Window.open(href, "_blank", "");
                }
            } else {
                Window.open(href, "_blank", "");
            }
        } catch (final Exception e) {
            GWT.log(e.getMessage(), e);
        }
    }

    private void openAnnotation(final String href) {
        final Long annotationId = getLongParam(href, "annotationId");
        if (annotationId != null) {
            EditAnnotationEvent.fire(this, annotationId);

        } else {
            final Long streamId = getLongParam(href.toLowerCase(Locale.ROOT), "streamId".toLowerCase(Locale.ROOT));
            final Long eventId = getLongParam(href.toLowerCase(Locale.ROOT), "eventId".toLowerCase(Locale.ROOT));
            final String eventIdList = getParam(href, "eventIdList");
            String title = getParam(href, "title");
            final String subject = getParam(href, "subject");
            final String status = getParam(href, "status");
            final String assignedTo = getParam(href, "assignedTo");
            final String comment = getParam(href, "comment");

            title = title == null
                    ? "New Annotation"
                    : title;

            final List<EventId> linkedEvents = new ArrayList<>();
            if (streamId != null && eventId != null) {
                linkedEvents.add(new EventId(streamId, eventId));
            }
            EventId.parseList(eventIdList, linkedEvents);

            UserRef initialAssignTo = null;
            if (assignedTo != null) {
                initialAssignTo = UserRef.builder().uuid(assignedTo).build();
            }

            CreateAnnotationEvent.fire(
                    this,
                    title,
                    subject,
                    status,
                    initialAssignTo,
                    comment,
                    null,
                    linkedEvents,
                    null);
        }
    }

    private void openData(final String href) {
        final long id = getParam(href, "id", -1);
        final long partIndex = getParam(href, "partNo", 1) - 1; // convert to zero based
        final long recordIndex = getParam(href, "recordNo", 1) - 1; // convert to zero based
        final int lineFrom = (int) getParam(href, "lineFrom", -1);
        final int colFrom = (int) getParam(href, "colFrom", -1);
        final int lineTo = (int) getParam(href, "lineTo", -1);
        final int colTo = (int) getParam(href, "colTo", -1);
        final DataViewType dataViewType = getParam(
                href,
                "viewType",
                DataViewType::parse,
                DataViewType.PREVIEW);
        final DisplayMode displayMode = getParam(
                href,
                "displayType",
                DisplayMode::parse,
                DisplayMode.DIALOG);

        final SourceLocation.Builder builder = SourceLocation.builder(id)
                .withPartIndex(partIndex)
                .withRecordIndex(recordIndex);

        // In preview mode we only want to see the range requested, non-preview
        // we want to see it all but with the selected range highlighted
        if (DataViewType.PREVIEW.equals(dataViewType)) {
            builder
                    .withDataRangeBuilder(dataRangeBuilder -> {
                        if (lineFrom != -1 && colFrom != -1) {
                            dataRangeBuilder.fromLocation(new DefaultLocation(lineFrom, colFrom));
                        }
                        if (lineTo != -1 && colTo != -1) {
                            dataRangeBuilder.toLocation(new DefaultLocation(lineTo, colTo));
                        }
                    });
        }

        // Add the highlight in case the user clicks through to the source view, then we can
        // highlight the range in there.  Can't highlight in data preview as the data may be formatted
        // so the line/cols won't marry up.
        if (lineFrom != -1 && colFrom != -1 && lineTo != -1 && colTo != -1) {
            builder.withHighlight(new TextRange(
                    new DefaultLocation(lineFrom, colFrom),
                    new DefaultLocation(lineTo, colTo)));
        }

        ShowDataEvent.fire(
                this,
                builder.build(),
                dataViewType,
                displayMode);
//        if (isPreview) {
//            ShowDataEvent.fire(this, sourceLocation);
//        } else {
//            ShowSourceEvent.fire(this, sourceLocation, displayMode);
//        }
    }

    private void openStepping(final String href) {
        final long id = getParam(href, "id", -1);
        final long partIndex = getParam(href, "partNo", 1) - 1; // convert to zero based
        final long recordIndex = getParam(href, "recordNo", 1) - 1; // convert to zero based
        BeginPipelineSteppingEvent.fire(
                this,
                null,
                null,
                StepType.REFRESH,
                new StepLocation(id, partIndex, recordIndex),
                null);
    }

    private void openDialog(final Hyperlink hyperlink, final String customTitle) {
        final PopupSize popupSize = PopupSize.resizable(800, 600);
        final IFramePresenter presenter = iFramePresenterProvider.get();
        final HandlerRegistration handlerRegistration = presenter.addDirtyHandler(event1 ->
                RenamePopupEvent.builder(presenter).caption(presenter.getLabel()).fire());
        presenter.setUrl(hyperlink.getHref());
        presenter.setCustomTitle(customTitle);

        ShowPopupEvent.builder(presenter)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(popupSize)
                .caption(presenter.getLabel())
                .onHide(e -> {
                    handlerRegistration.removeHandler();
                    presenter.close();
                })
                .fire();
    }

    private void openTab(final Hyperlink hyperlink, final String customTitle) {
        final IFrameContentPresenter presenter = iFrameContentPresenterProvider.get();
        presenter.setUrl(hyperlink.getHref());
        presenter.setCustomTitle(customTitle);
        presenter.setIcon(hyperlink.getIcon());
        final CloseContentEvent.Handler handler = event ->
                ConfirmEvent.fire(this,
                        "Are you sure you want to close?",
                        res -> {
                            if (res) {
                                presenter.close();
                            }
                            event.getCallback().closeTab(res);
                        });
        contentManager.open(
                handler,
                presenter,
                presenter);
    }

    private <T> T getParam(final String href,
                           final String paramName,
                           final Function<String, T> parseFunction,
                           final T defaultValue) {
        final String value = getParam(href, paramName);
        if (value == null || value.length() == 0) {
            return defaultValue;
        } else {
            return parseFunction.apply(value);
        }
    }

    private boolean getBooleanParam(final String href,
                                    final String paramName,
                                    final boolean defaultValue) {
        final String value = getParam(href, paramName);
        if (value == null || value.length() == 0) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(value);
        }
    }

    private long getParam(final String href, final String paramName, final long def) {
        final String value = getParam(href, paramName);
        if (value == null || value.length() == 0) {
            return def;
        }
        return Long.parseLong(value);
    }

    private Long getLongParam(final String href, final String paramName) {
        final String value = getParam(href, paramName);
        if (value == null || value.length() == 0) {
            return null;
        }
        return Long.valueOf(value);
    }

    private String getParam(final String href, final String paramName) {
        String value = null;
        int start = href.indexOf(paramName + "=");
        if (start != -1) {
            start = start + (paramName + "=").length();
            final int end = href.indexOf("&", start);
            if (end == -1) {
                value = href.substring(start);
            } else {
                value = href.substring(start, end);
            }
        }
        if (value != null) {
            value = URL.decodeQueryString(value);
        }

        return value;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
