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
import com.google.gwt.dom.client.SelectElement;
import com.google.gwt.dom.client.TextAreaElement;
import com.google.gwt.user.client.ui.HTML;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.activity.client.ActivityEditPresenter.ActivityEditView;
import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.activity.shared.Activity.Prop;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ActivityEditPresenter extends MyPresenterWidget<ActivityEditView> {
    private static final String DEFAULT_ACTIVITY_EDITOR_TITLE = "Edit Activity";
    private static final String DEFAULT_ACTIVITY_EDITOR_BODY = "" +
            "Activity Code:</br>" +
            "<input type=\"text\" name=\"code\"></input></br></br>" +
            "Activity Description:</br>" +
            "<textarea rows=\"4\" style=\"width:100%;height:80px\" name=\"description\"></textarea>" +
            "Explain what the activity is";

    private final ClientDispatchAsync dispatcher;

    private boolean activityRecordingEnabled = true;
    private String activityEditorTitle = DEFAULT_ACTIVITY_EDITOR_TITLE;
    private String activityEditorBody = DEFAULT_ACTIVITY_EDITOR_BODY;

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
                    activityEditorBody = result.get(ClientProperties.ACTIVITY_EDITOR_BODY, DEFAULT_ACTIVITY_EDITOR_BODY);
                })
                .onFailure(caught -> AlertEvent.fireError(ActivityEditPresenter.this, caught.getMessage(), null));
    }

    public void show(final Activity activity, final Consumer<Activity> consumer) {
        this.activity = activity;
        if (activityRecordingEnabled) {
            getView().getHtml().setHTML(activityEditorBody);

            // Set the properties.
            Scheduler.get().scheduleDeferred(this::read);

            final PopupUiHandlers internalPopupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    if (ok) {
                        write(consumer);
                    } else {
                        consumer.accept(activity);
                        hide();
                    }
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                }
            };

            final PopupSize popupSize = new PopupSize(640, 480, true);
            ShowPopupEvent.fire(this,
                    this,
                    PopupType.OK_CANCEL_DIALOG,
                    popupSize, activityEditorTitle,
                    internalPopupUiHandlers);
        }
    }

    private void hide() {
        HidePopupEvent.fire(ActivityEditPresenter.this, ActivityEditPresenter.this);
    }

    protected void read() {
        boolean doneFocus = false;

        final List<Element> inputElements = new ArrayList<>();
        findInputElements(getView().getHtml().getElement().getChildNodes(), inputElements);

        for (final Element element : inputElements) {
            final String tagName = element.getTagName();
            if ("input".equalsIgnoreCase(tagName)) {
                // Focus the first input.
                if (!doneFocus) {
                    doneFocus = true;
                    element.focus();
                }

                final Prop prop = createProp(element);
                final String value = getValue(activity, prop.getId());
                if (value != null) {
                    final InputElement inputElement = element.cast();

                    if ("checkbox".equalsIgnoreCase(inputElement.getType()) || "radio".equalsIgnoreCase(inputElement.getType())) {
                        try {
                            inputElement.setChecked(Boolean.valueOf(value));
                        } catch (final RuntimeException e) {
                            // Ignore.
                        }
                    } else {
                        inputElement.setValue(value);
                    }
                }
            } else if ("text".equalsIgnoreCase(tagName)) {
                // Focus the first input.
                if (!doneFocus) {
                    doneFocus = true;
                    element.focus();
                }

                final Prop prop = createProp(element);
                final String value = getValue(activity, prop.getId());
                if (value != null) {
                    final InputElement inputElement = element.cast();
                    inputElement.setValue(value);
                }
            } else if ("textarea".equalsIgnoreCase(tagName)) {
                // Focus the first input.
                if (!doneFocus) {
                    doneFocus = true;
                    element.focus();
                }

                final Prop prop = createProp(element);
                final String value = getValue(activity, prop.getId());
                if (value != null) {
                    final TextAreaElement inputElement = element.cast();
                    inputElement.setValue(value);
                }
            } else if ("select".equalsIgnoreCase(tagName)) {
                // Focus the first input.
                if (!doneFocus) {
                    doneFocus = true;
                    element.focus();
                }

                final Prop prop = createProp(element);
                final String value = getValue(activity, prop.getId());
                if (value != null) {
                    final SelectElement selectElement = element.cast();
                    selectElement.setValue(value);
                }
            }
        }
    }

    protected void write(final Consumer<Activity> consumer) {
        final ActivityDetails details = new ActivityDetails();

        final List<Element> inputElements = new ArrayList<>();
        findInputElements(getView().getHtml().getElement().getChildNodes(), inputElements);

        for (final Element element : inputElements) {
            final String tagName = element.getTagName();
            if ("input".equalsIgnoreCase(tagName)) {
                final Prop prop = createProp(element);
                final InputElement inputElement = element.cast();

                if ("checkbox".equalsIgnoreCase(inputElement.getType()) || "radio".equalsIgnoreCase(inputElement.getType())) {
                    details.add(prop, Boolean.toString(inputElement.isChecked()));
                } else {
                    details.add(prop, inputElement.getValue());
                }
            } else if ("text".equalsIgnoreCase(tagName)) {
                final Prop prop = createProp(element);
                final InputElement inputElement = element.cast();
                details.add(prop, inputElement.getValue());
            } else if ("textarea".equalsIgnoreCase(tagName)) {
                final Prop prop = createProp(element);
                final TextAreaElement inputElement = element.cast();
                details.add(prop, inputElement.getValue());
            } else if ("select".equalsIgnoreCase(tagName)) {
                final Prop prop = createProp(element);
                final SelectElement selectElement = element.cast();
                details.add(prop, selectElement.getValue());
            }
        }
        activity.setDetails(details);

        // Save the activity.
        dispatcher.exec(new EntityServiceSaveAction<Activity>(activity)).onSuccess(result -> {
            activity = result;
            consumer.accept(result);
            hide();
        });
    }

    private void findInputElements(final NodeList<Node> nodes, final List<Element> inputElements) {
        for (int i = 0; i < nodes.getLength(); i++) {
            final Node node = nodes.getItem(i);
            if (node instanceof Element) {
                final Element element = (Element) node;
                final String tagName = element.getTagName();
                if ("input".equalsIgnoreCase(tagName)) {
                    final Prop prop = createProp(element);
                    if (prop.getName() != null) {
                        inputElements.add(element);
                    }
                } else if ("text".equalsIgnoreCase(tagName)) {
                    final Prop prop = createProp(element);
                    if (prop.getName() != null) {
                        inputElements.add(element);
                    }
                } else if ("textarea".equalsIgnoreCase(tagName)) {
                    final Prop prop = createProp(element);
                    if (prop.getName() != null) {
                        inputElements.add(element);
                    }
                } else if ("select".equalsIgnoreCase(tagName)) {
                    final Prop prop = createProp(element);
                    if (prop.getName() != null) {
                        inputElements.add(element);
                    }
                }
            }

            findInputElements(node.getChildNodes(), inputElements);
        }
    }

    private Prop createProp(final Element element) {
        final Prop prop = new Prop();
        prop.setId(getId(element));
        prop.setName(getName(element));
        prop.setShowInSelection(isShowInSelection(element));
        prop.setShowInList(isShowInList(element));

        if (prop.getId() == null) {
            // Fall back to using name.
            prop.setId(prop.getName());
        }

        if (prop.getName() == null) {
            // Fall back to using id.
            prop.setName(prop.getId());
        }

        return prop;
    }

    private String getId(final Element element) {
        final String id = element.getId();
        if (id != null) {
            final String trimmed = id.trim();
            if (trimmed.length() > 0) {
                return trimmed;
            }
        }
        return null;
    }

    private String getName(final Element element) {
        final String name = element.getAttribute("name");
        if (name != null) {
            final String trimmed = name.trim();
            if (trimmed.length() > 0) {
                return trimmed;
            }
        }
        return null;
    }

    private String getValue(final Activity activity, final String propertyId) {
        if (activity != null && activity.getDetails() != null) {
            return activity.getDetails().value(propertyId);
        }
        return null;
    }

    private boolean isShowInSelection(final Element element) {
        final String att = element.getAttribute("showInSelection");
        if (att != null) {
            final String trimmed = att.trim();
            if (trimmed.length() > 0) {
                return !trimmed.equalsIgnoreCase("false");
            }
        }
        return true;
    }

    private boolean isShowInList(final Element element) {
        final String att = element.getAttribute("showInList");
        if (att != null) {
            final String trimmed = att.trim();
            if (trimmed.length() > 0) {
                return !trimmed.equalsIgnoreCase("false");
            }
        }
        return true;
    }

    public interface ActivityEditView extends View {
        HTML getHtml();
    }
}