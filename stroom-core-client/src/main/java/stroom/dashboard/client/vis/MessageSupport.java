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

package stroom.dashboard.client.vis;

import stroom.dashboard.client.vis.PostMessage.FrameListener;
import stroom.dashboard.client.vis.VisTooltipPlacement.Direction;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.query.client.presenter.VisComponentSelection;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.JSONUtil;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNull;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Window;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class MessageSupport
        implements FrameListener, HasHandlers, HasUiHandlers<SelectionUiHandlers> {

    private static final Map<Integer, Callback<String, Exception>> callbacks = new HashMap<>();
    private static int frameIdCounter;
    private static int callbackId;
    private final EventBus eventBus;
    private final Element frame;
    private final int frameId;
    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    private SelectionUiHandlers uiHandlers;
    private Consumer<List<VisToolbarButton>> toolbarHandler;
    private BiFunction<String, List<String>, List<String>> coloursHandler;

    public MessageSupport(final EventBus eventBus,
                          final Element frame) {
        this.eventBus = eventBus;
        this.frame = frame;
        frameIdCounter++;
        frameId = frameIdCounter;
    }

    @Override
    public void setUiHandlers(final SelectionUiHandlers uiHandlers) {
        this.uiHandlers = uiHandlers;
    }

    /// Sets what to do when the visualisation asks for buttons in its toolbar
    /// (`stroom.setToolbarButtons` in `vis.js`).
    ///
    /// @param toolbarHandler Given the buttons it wants, replacing any it asked for before; an empty
    ///                       list when it wants none.
    public void setToolbarHandler(final Consumer<List<VisToolbarButton>> toolbarHandler) {
        this.toolbarHandler = toolbarHandler;
    }

    /// Sets what gives the visualisation the colours of its values when it asks for them
    /// (`stroom.colours` in `vis.js`), so that a value has the same colour in every visualisation
    /// of a dashboard.
    ///
    /// @param coloursHandler Given a field and some of its values, gives each value's colour,
    ///                       `#rrggbb`, in the same order.
    public void setColoursHandler(final BiFunction<String, List<String>, List<String>> coloursHandler) {
        this.coloursHandler = coloursHandler;
    }

    public void bind() {
        PostMessage.get().addFrameListener(this);
    }

    public void unbind() {
        PostMessage.get().removeFrameListener(this);
        // The frame is going, so it can no longer ask for its tooltip to be hidden
        VisTooltip.hide(frameId);
    }

    public void postMessage(final JSONObject json, final Callback<String, Exception> callback) {
        callbackId++;
        if (callbackId > 100) {
            callbackId = 1;
        }

        callbacks.put(callbackId, callback);

        PostMessage.get().postMessage(frame, json, frameId, callbackId);
    }

    public void postMessage(final JSONObject json) {
        PostMessage.get().postMessage(frame, json, frameId);
    }

    @Override
    public void receiveMessage(final MessageEvent event, final JSONObject message) {
        final Integer callbackId = JSONUtil.getInteger(message.get("callbackId"));
        final String functionName = JSONUtil.getString(message.get("functionName"));

        if ("link".equals(functionName)) {
            final String href = JSONUtil.getString(message.get("href"));
            final String target = JSONUtil.getString(message.get("target"));
            final Hyperlink hyperlink = Hyperlink.builder().href(href).type(target).build();
            HyperlinkEvent.fire(this, hyperlink, taskMonitorFactory);

        } else if ("select".equals(functionName)) {
            final JSONValue selection = message.get("selection");
            GWT.log("Received selection from vis: " + selection.toString());

            if (uiHandlers != null) {
                uiHandlers.onSelection(VisComponentSelection.create(selection));
            }

        } else if ("showTooltip".equals(functionName)) {
            showTooltip(message);

        } else if ("hideTooltip".equals(functionName)) {
            VisTooltip.hide(frameId);

        } else if ("setToolbarButtons".equals(functionName)) {
            if (toolbarHandler != null) {
                toolbarHandler.accept(toolbarButtons(message));
            }

        } else if ("colours".equals(functionName)) {
            answerColours(message);

        } else {
            // Test to see if this is a callback message.
            if (callbackId != null) {
                final Callback<String, Exception> callback = callbacks.remove(callbackId);
                if (callback != null) {
                    final String param = JSONUtil.getString(message.get("param"));
                    if ("onSuccess".equals(functionName)) {
                        callback.onSuccess(param);
                    } else if ("onFailure".equals(functionName)) {
                        callback.onFailure(new RuntimeException(param));
                    }
                }
            } else {
                final String exception = JSONUtil.getString(message.get("exception"));
                if (exception != null) {
                    Window.alert(exception);
                } else {
                    Window.alert("Unexpected message - " + message);
                }
            }
        }
    }

    // Answers a colours message, {requestId, field, values}, with the values' colours, by calling
    // visualisationManager.colours(requestId, colours) in the frame
    private void answerColours(final JSONObject message) {
        if (coloursHandler == null) {
            return;
        }
        final String[] values = JSONUtil.getStrings(message.get("values"));
        final List<String> colours = coloursHandler.apply(
                JSONUtil.getString(message.get("field")),
                values == null
                        ? new ArrayList<>()
                        : Arrays.asList(values));
        final JSONArray coloursArray = new JSONArray();
        for (int i = 0; i < colours.size(); i++) {
            coloursArray.set(i, new JSONString(colours.get(i)));
        }
        final JSONArray params = new JSONArray();
        final Integer requestId = JSONUtil.getInteger(message.get("requestId"));
        params.set(0, requestId == null
                ? JSONNull.getInstance()
                : new JSONNumber(requestId));
        params.set(1, coloursArray);
        final JSONObject reply = new JSONObject();
        reply.put("functionName", new JSONString("visualisationManager.colours"));
        reply.put("params", params);
        postMessage(reply);
    }

    private void showTooltip(final JSONObject message) {
        final JSONObject rect = JSONUtil.getObject(message.get("rect"));
        if (rect != null) {
            final double left = getDouble(rect, "left");
            final double top = getDouble(rect, "top");
            final Rect rectInFrame = new Rect(
                    top,
                    top + getDouble(rect, "height"),
                    left,
                    left + getDouble(rect, "width"));
            VisTooltip.show(
                    frameId,
                    frame,
                    JSONUtil.getString(message.get("html")),
                    rectInFrame,
                    Direction.fromCode(JSONUtil.getString(message.get("direction"))));
        }
    }

    // The buttons of a setToolbarButtons message, leaving out any without an id
    private static List<VisToolbarButton> toolbarButtons(final JSONObject message) {
        final List<VisToolbarButton> buttons = new ArrayList<>();
        final JSONArray array = JSONUtil.getArray(message.get("buttons"));
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                final JSONObject button = JSONUtil.getObject(array.get(i));
                final String id = button == null
                        ? null
                        : JSONUtil.getString(button.get("id"));
                if (id != null) {
                    final JSONValue pressed = button.get("pressed");
                    final JSONValue enabled = button.get("enabled");
                    buttons.add(new VisToolbarButton(
                            id,
                            JSONUtil.getString(button.get("title")),
                            JSONUtil.getString(button.get("icon")),
                            pressed != null && pressed.isBoolean() != null
                                    ? pressed.isBoolean().booleanValue()
                                    : null,
                            // Enabled unless it says otherwise (an older vis.js doesn't say)
                            enabled == null || enabled.isBoolean() == null || enabled.isBoolean().booleanValue()));
                }
            }
        }
        return buttons;
    }

    private static double getDouble(final JSONObject object, final String key) {
        final Double value = JSONUtil.getDouble(object.get(key));
        return value != null
                ? value
                : 0;
    }

    @Override
    public int getFrameId() {
        return frameId;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    /// Says what shows the progress of the requests this makes, e.g. the presenter that uses it.
    ///
    /// @param taskMonitorFactory What shows their progress.
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }
}
