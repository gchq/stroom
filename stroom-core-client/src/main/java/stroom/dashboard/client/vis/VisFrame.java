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

package stroom.dashboard.client.vis;

import stroom.script.shared.ScriptDoc;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.visualisation.client.presenter.VisFunction;
import stroom.visualisation.client.presenter.VisFunction.LoadStatus;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.http.client.URL;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

public class VisFrame extends Composite implements VisPane, HasTaskMonitorFactory {

    private final MessageSupport messageSupport;
    private VisFunction function;
    private final SimplePanel container;
    private final Frame frame;

    public VisFrame(final EventBus eventBus) {
        // + "?time=" + System.currentTimeMillis());
        frame = new Frame("ui/vis.html");
        frame.addStyleName("VisFrame-frame");
        messageSupport = new MessageSupport(eventBus, frame.getElement());

        container = new SimplePanel(frame);
        container.addStyleName("VisFrame-container");
        initWidget(container);
    }

    public void setContainerPositionAndSize(final double left,
                                            final double top,
                                            final double width,
                                            final double height) {
        setPositionAndSize(
                container.getElement().getStyle(),
                left,
                top,
                width,
                height);
    }

    public void setInnerPositionAndSize(final double left,
                                        final double top,
                                        final double width,
                                        final double height) {
        setPositionAndSize(
                frame.getElement().getStyle(),
                left,
                top,
                width,
                height);
    }

    private void setPositionAndSize(final Style style,
                                    final double left,
                                    final double top,
                                    final double width,
                                    final double height) {
        style.setLeft(left, Unit.PX);
        style.setTop(top, Unit.PX);
        style.setWidth(width, Unit.PX);
        style.setHeight(height, Unit.PX);
    }

    public void bind() {
        messageSupport.bind();
    }

    public void unbind() {
        messageSupport.unbind();
    }

    @Override
    public void setUiHandlers(final SelectionUiHandlers uiHandlers) {
        messageSupport.setUiHandlers(uiHandlers);
    }

    @Override
    public void injectScripts(final List<ScriptDoc> scripts, final VisFunction function) {
        injectScriptsFromURL(scripts, function);
    }

    private void injectScriptsFromURL(final List<ScriptDoc> scripts, final VisFunction function) {
        this.function = function;

        if (scripts == null || scripts.size() == 0) {
            // Set the function status to loaded. This will tell all handlers
            // that the function is ready for use.
            if (!LoadStatus.FAILURE.equals(function.getStatus())) {
                function.setStatus(LoadStatus.LOADED);
            }

        } else {
            // Only load script if we haven't already had a failure.
            if (!LoadStatus.FAILURE.equals(function.getStatus())) {
                final Callback<String, Exception> callback = new Callback<String, Exception>() {
                    @Override
                    public void onSuccess(final String result) {
                        // Set the function status to loaded. This will tell all
                        // handlers that the function is ready for use.
                        if (!LoadStatus.FAILURE.equals(function.getStatus())) {
                            function.setStatus(LoadStatus.LOADED);
                        }
                    }

                    @Override
                    public void onFailure(final Exception e) {
                        // Show failure message.
                        failure(e.getMessage());
                    }
                };

                final JSONArray arr = new JSONArray();
                for (int i = 0; i < scripts.size(); i++) {
                    final ScriptDoc script = scripts.get(i);
                    final String url = createURL(script);

                    final JSONObject obj = new JSONObject();
                    obj.put("name", new JSONString(script.getName()));
                    obj.put("url", new JSONString(url));

                    arr.set(i, obj);
                }

                injectScripts(arr, callback);

            } else {
                // Inject the next script in the list.
                injectScriptsFromURL(scripts, function);
            }
        }
    }

    private void failure(final String message) {
        if (!LoadStatus.FAILURE.equals(function.getStatus())) {
            function.setStatus(LoadStatus.FAILURE, message);
        }
    }

    private String createURL(final ScriptDoc script) {
        final StringBuilder sb = new StringBuilder();
        sb.append("script?");
        sb.append("uuid=");
        sb.append(script.getUuid());
        sb.append("&name=");
        sb.append(script.getName());
        sb.append("&ver=");
        sb.append(script.getVersion());
        return URL.encode(sb.toString());
    }

    private void injectScripts(final JSONValue scripts, final Callback<String, Exception> callback) {
        final JSONArray params = new JSONArray();
        params.set(0, scripts);

        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.injectScripts"));
        message.put("params", params);

        messageSupport.postMessage(message, callback);
    }

    @Override
    public void setClassName(final String className) {
        final JSONArray params = new JSONArray();
        params.set(0, new JSONString(className));

        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.setClassName"));
        message.put("params", params);
        messageSupport.postMessage(message);
    }

    @Override
    public void start() {
        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.start"));
        messageSupport.postMessage(message);
    }

    @Override
    public void end() {
        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.end"));
        messageSupport.postMessage(message);
    }

    @Override
    public void setVisType(final String visType, final String className) {
        final JSONArray params = new JSONArray();
        params.set(0, new JSONString(visType));
        params.set(1, new JSONString(className));

        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.setVisType"));
        message.put("params", params);

        final Callback<String, Exception> callback = new Callback<String, Exception>() {
            @Override
            public void onSuccess(final String result) {
                // Ignore.
            }

            @Override
            public void onFailure(final Exception e) {
                // Show failure message.
                failure(e.getMessage());
            }
        };

        messageSupport.postMessage(message, callback);
    }

    @Override
    public void setData(final JavaScriptObject context, final JavaScriptObject settings, final JavaScriptObject data) {
        final JSONArray params = new JSONArray();
        params.set(0, new JSONObject(context));
        params.set(1, new JSONObject(settings));
        params.set(2, new JSONObject(data));

        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.setData"));
        message.put("params", params);

        messageSupport.postMessage(message);
    }

    @Override
    public void onResize() {
        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.resize"));
        messageSupport.postMessage(message);
    }

    @Override
    public void removeFromParent() {
        // Do nothing...
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        messageSupport.setTaskMonitorFactory(taskMonitorFactory);
    }
}
