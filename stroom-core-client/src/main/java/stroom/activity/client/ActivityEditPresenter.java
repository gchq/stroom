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

package stroom.activity.client;

import stroom.activity.client.ActivityEditPresenter.ActivityEditView;
import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.activity.shared.Activity.Prop;
import stroom.activity.shared.ActivityResource;
import stroom.activity.shared.ActivityValidationResult;
import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ActivityConfig;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ActivityEditPresenter
        extends MyPresenterWidget<ActivityEditView> {

    private static final ActivityResource ACTIVITY_RESOURCE = GWT.create(ActivityResource.class);

    private final RestFactory restFactory;
    private final UiConfigCache uiConfigCache;

    private Activity activity;

    @Inject
    public ActivityEditPresenter(final EventBus eventBus,
                                 final ActivityEditView view,
                                 final RestFactory restFactory,
                                 final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;
    }

    public void show(final Activity activity,
                     final Consumer<Activity> consumer) {
        this.activity = activity;
        uiConfigCache.get(result -> {
            if (result != null) {
                final ActivityConfig activityConfig = result.getActivity();
                final boolean activityRecordingEnabled = activityConfig.isEnabled();
                final String activityEditorTitle = activityConfig.getEditorTitle();
                final String activityEditorBody = activityConfig.getEditorBody();

                if (activityRecordingEnabled) {
                    getView().getHtml().setHTML(activityEditorBody);

                    final PopupSize popupSize = PopupSize.resizable(640, 480);
                    ShowPopupEvent.builder(this)
                            .popupType(PopupType.OK_CANCEL_DIALOG)
                            .popupSize(popupSize)
                            .caption(activityEditorTitle)
                            .modal(true)
                            .onShow(e -> read())
                            .onHideRequest(e -> {
                                if (e.isOk()) {
                                    write(consumer, e);
                                } else {
                                    consumer.accept(activity);
                                    e.hide();
                                }
                            })
                            .fire();
                }
            }
        }, this);
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

                    if ("checkbox".equalsIgnoreCase(inputElement.getType())
                            || "radio".equalsIgnoreCase(inputElement.getType())) {
                        try {
                            inputElement.setChecked(Boolean.parseBoolean(value));
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

    protected void write(final Consumer<Activity> consumer,
                         final HidePopupRequestEvent event) {
        final List<Element> inputElements = new ArrayList<>();
        findInputElements(getView().getHtml().getElement().getChildNodes(), inputElements);

        final List<Prop> properties = new ArrayList<>();
        for (final Element element : inputElements) {
            final String tagName = element.getTagName();
            if ("input".equalsIgnoreCase(tagName)) {
                final InputElement inputElement = element.cast();
                if ("checkbox".equalsIgnoreCase(inputElement.getType())
                        || "radio".equalsIgnoreCase(inputElement.getType())) {
                    properties.add(createProp(element, Boolean.toString(inputElement.isChecked())));
                } else {
                    properties.add(createProp(element, inputElement.getValue()));
                }
            } else if ("text".equalsIgnoreCase(tagName)) {
                final InputElement inputElement = element.cast();
                properties.add(createProp(element, inputElement.getValue()));
            } else if ("textarea".equalsIgnoreCase(tagName)) {
                final TextAreaElement inputElement = element.cast();
                properties.add(createProp(element, inputElement.getValue()));
            } else if ("select".equalsIgnoreCase(tagName)) {
                final SelectElement selectElement = element.cast();
                properties.add(createProp(element, selectElement.getValue()));
            }
        }

        final ActivityDetails details = new ActivityDetails(properties);
        activity.setDetails(details);

        // Validate the activity.
        restFactory
                .create(ACTIVITY_RESOURCE)
                .method(res -> res.validate(activity))
                .onSuccess(result -> afterValidation(result, details, consumer, event))
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(this)
                .exec();
    }

    private void afterValidation(final ActivityValidationResult validationResult,
                                 final ActivityDetails details,
                                 final Consumer<Activity> consumer,
                                 final HidePopupRequestEvent event) {
        if (!validationResult.isValid()) {
            AlertEvent.fireWarn(
                    ActivityEditPresenter.this,
                    "Validation Error",
                    validationResult.getMessages(),
                    event::reset);

        } else {
            // Save the activity.
            if (activity.getId() == null) {
                restFactory
                        .create(ACTIVITY_RESOURCE)
                        .method(ActivityResource::create)
                        .onSuccess(result -> {
                            activity = result;
                            activity.setDetails(details);

                            update(activity, details, consumer, event);
                        })
                        .onFailure(RestErrorHandler.forPopup(this, event))
                        .taskMonitorFactory(this)
                        .exec();
            } else {
                update(activity, details, consumer, event);
            }
        }
    }

    private void update(final Activity activity,
                        final ActivityDetails details,
                        final Consumer<Activity> consumer,
                        final HidePopupRequestEvent event) {
        restFactory
                .create(ACTIVITY_RESOURCE)
                .method(res -> res.update(activity.getId(), activity))
                .onSuccess(result -> {
                    ActivityEditPresenter.this.activity = result;
                    consumer.accept(result);
                    event.hide();
                })
                .onFailure(RestErrorHandler.forPopup(this, event))
                .taskMonitorFactory(this)
                .exec();
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
        prop.setValidation(getValidation(element));
        prop.setValidationMessage(getValidationMessage(element));
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

    private Prop createProp(final Element element, final String value) {
        final Prop prop = createProp(element);
        prop.setValue(value);
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

    private String getValidation(final Element element) {
        final String validation = element.getAttribute("validation");
        if (validation != null) {
            final String trimmed = validation.trim();
            if (trimmed.length() > 0) {
                return trimmed;
            }
        }
        return null;
    }

    private String getValidationMessage(final Element element) {
        final String validationMessage = element.getAttribute("validationMessage");
        if (validationMessage != null) {
            final String trimmed = validationMessage.trim();
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
