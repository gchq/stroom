/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.client;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.TextAreaElement;
import com.google.gwt.user.client.ui.HTML;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.activity.client.ActivityEditPresenter.ActivityEditView;
import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.EntityServiceSaveAction;
import stroom.node.client.ClientPropertyCache;
import stroom.node.shared.ClientProperties;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.Map;

public class ActivityEditPresenter extends MyPresenterWidget<ActivityEditView> {
    private static final String DEFAULT_ACTIVITY_EDITOR_TITLE = "Edit Activity";
    private static final String DEFAULT_ACTIVITY_EDITOR_BODY = "" +
            "Activity Code:</br>" +
            "<input type=\"text\" name=\"code\"></input></br></br>" +
            "Activity Description:</br>" +
            "<textarea rows=\"4\" style=\"width:100%;height:80px\" name=\"description\"></textarea>" +
            "Explain what the activity is";

//    private static final String DEFAULT_QUERY_INFO_VALIDATION_REGEX = "^[\\s\\S]{3,}$";


    private final ClientDispatchAsync dispatcher;

    private boolean activityRecordingEnabled = true;
    private String activityEditorTitle = DEFAULT_ACTIVITY_EDITOR_TITLE;
    private String activityEditorBody = DEFAULT_ACTIVITY_EDITOR_BODY;
//    private String queryInfoPopupValidationRegex = DEFAULT_QUERY_INFO_VALIDATION_REGEX;

    private Activity activity;

    @Inject
    public ActivityEditPresenter(final EventBus eventBus,
                                 final ActivityEditView view,
                                 final ClientDispatchAsync dispatcher,
                                 final ClientPropertyCache clientPropertyCache) {
        super(eventBus, view);
        this.dispatcher = dispatcher;

        clientPropertyCache.get()
                .onSuccess(result -> {
                    activityRecordingEnabled = result.getBoolean(ClientProperties.ACTIVITY_ENABLED, true);
                    activityEditorTitle = result.get(ClientProperties.ACTIVITY_EDITOR_TITLE, DEFAULT_ACTIVITY_EDITOR_TITLE);
//                    activityEditorBody = result.get(ClientProperties.ACTIVITY_EDITOR_BODY, DEFAULT_ACTIVITY_EDITOR_BODY);
//                    queryInfoPopupValidationRegex = result.get(ClientProperties.QUERY_INFO_POPUP_VALIDATION_REGEX, DEFAULT_QUERY_INFO_VALIDATION_REGEX);
                })
                .onFailure(caught -> AlertEvent.fireError(ActivityEditPresenter.this, caught.getMessage(), null));
    }

    public void show(final Activity activity, final PopupUiHandlers popupUiHandlers) {
        this.activity = activity;
        if (activityRecordingEnabled) {
            getView().getHtml().setHTML(activityEditorBody);

            // Set the properties.
            Scheduler.get().scheduleDeferred(() -> {
                read();
            });

            final PopupUiHandlers internalPopupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        write(true);
                    } else {
                        hide();
                    }

                    popupUiHandlers.onHideRequest(autoClose, ok);
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                    popupUiHandlers.onHide(autoClose, ok);
                }
            };

            final PopupSize popupSize = new PopupSize(640, 480, true);
            ShowPopupEvent.fire(this,
                    this,
                    PopupType.OK_CANCEL_DIALOG,
                    popupSize, activityEditorTitle,
                    internalPopupUiHandlers);
        }
//        else {
//            consumer.accept(new State(null, true));
//        }
    }

    private void hide() {
        HidePopupEvent.fire(ActivityEditPresenter.this, ActivityEditPresenter.this);
    }

    protected void read() {
        boolean doneFocus = false;

        final NodeList<Node> nodes = getView().getHtml().getElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            final Node node = nodes.getItem(i);
            if (node instanceof Element) {
                final Element element = (Element) node;
                final String tagName = element.getTagName();
                if ("text".equalsIgnoreCase(tagName) || "input".equalsIgnoreCase(tagName)) {
                    // Focus the first input.
                    if (!doneFocus) {
                        doneFocus = true;
                        element.focus();
                    }

                    final String name = element.getAttribute("name");
                    if (name != null) {
                        final String value = getValue(activity, name);
                        if (value != null) {
                            final InputElement inputElement = element.cast();
                            inputElement.setValue(value);
                        }
                    }
                } else if ("textarea".equalsIgnoreCase(tagName)) {
                    // Focus the first input.
                    if (!doneFocus) {
                        doneFocus = true;
                        element.focus();
                    }

                    final String name = element.getAttribute("name");
                    if (name != null) {
                        final String value = getValue(activity, name);
                        if (value != null) {
                            final TextAreaElement inputElement = element.cast();
                            inputElement.setValue(value);
                        }
                    }
                }
            }
        }
    }

    protected void write(final boolean hideOnSave) {
        final ActivityDetails details = new ActivityDetails();
        final NodeList<Node> nodes = getView().getHtml().getElement().getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            final Node node = nodes.getItem(i);
            if (node instanceof Element) {
                final Element element = (Element) node;
                final String tagName = element.getTagName();
                if ("text".equalsIgnoreCase(tagName) || "input".equalsIgnoreCase(tagName)) {
                    final String name = element.getAttribute("name");
                    if (name != null) {
                        final InputElement inputElement = element.cast();
                        details.addProperty(name, inputElement.getValue());
                    }
                } else if ("textarea".equalsIgnoreCase(tagName)) {
                    final String name = element.getAttribute("name");
                    if (name != null) {
                        final TextAreaElement inputElement = element.cast();
                        details.addProperty(name, inputElement.getValue());
                    }
                }
            }
        }
        activity.setDetails(details);

        // Save the activity.
        dispatcher.exec(new EntityServiceSaveAction<Activity>(activity)).onSuccess(result -> {
            activity = result;
            if (hideOnSave) {
                hide();
            }
        });
    }

    private String getValue(final Activity activity, final String name) {
        if (activity != null) {
            final Map<String, String> properties = activity.getDetails().getProperties();
            if (properties != null) {
                return properties.get(name);
            }
        }
        return null;
    }

    public interface ActivityEditView extends View {
        HTML getHtml();
    }
}