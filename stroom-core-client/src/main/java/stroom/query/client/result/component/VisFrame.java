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

package stroom.query.client.result.component;

import stroom.preferences.client.DateTimeFormatter;
import stroom.query.client.result.table.ComponentSelection;
import stroom.query.client.result.vis.MessageSupport;
import stroom.query.client.result.vis.VisButtonSpec;
import stroom.script.shared.ScriptDoc;
import stroom.visualisation.client.VisFunction;
import stroom.visualisation.client.VisFunction.LoadStatus;
import stroom.widget.component.client.Component;
import stroom.widget.hyperlink.client.LinkValue;
import stroom.widget.text.client.Frame;
import stroom.widget.util.client.CssProperties;

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.LoadHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONNull;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/// A visualisation's frame: the page (`ui/vis.html`, or a visualisation's own assets) that runs it,
/// in a container that clips it, both placed by the view showing it. What Stroom says to the
/// visualisation (its scripts, data, settings, colours and time zone) goes in as messages through
/// its methods; what it asks of Stroom comes out through callbacks: toolbar buttons, colours, a
/// selection and a link to follow. [VisFrameController] opens its links and listens to it while it
/// is in use.
public class VisFrame extends Component implements RequiresResize {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final MessageSupport messageSupport;
    private VisFunction function;
    @UiField
    SimplePanel container;
    @UiField
    Frame frame;
    // The messages to send once the frame's page has loaded. vis.js only hears messages once it
    // has run, so one sent sooner, e.g. scripts to inject when the server replies quickly, is lost
    private final List<Runnable> pendingMessages = new ArrayList<>();
    private boolean frameLoaded;

    /// Makes a frame showing Stroom's visualisation page, e.g. one a template declares.
    public VisFrame() {
        initWidget(BINDER.createAndBindUi(this));
        frame.addLoadHandler(event -> onFrameLoaded());
        messageSupport = new MessageSupport(frame.getElement());
    }

    /// Sets the frame's page, for a visualisation that runs from its own assets rather than from
    /// scripts injected into Stroom's page.
    ///
    /// @param url The page's URL.
    public void setUrl(final String url) {
        frameLoaded = false;
        frame.setUrl(url);
    }

    private void onFrameLoaded() {
        frameLoaded = true;
        final List<Runnable> messages = new ArrayList<>(pendingMessages);
        pendingMessages.clear();
        messages.forEach(Runnable::run);
    }

    // Sends a message to the frame now if its page has loaded, or else once it has
    private void send(final Runnable message) {
        if (frameLoaded) {
            message.run();
        } else {
            pendingMessages.add(message);
        }
    }

    /// Names the frame for assistive technology, which announces a frame by its title (WCAG 4.1.2,
    /// Name, Role, Value).
    ///
    /// @param title The frame's name, e.g. the name of the dashboard component showing it.
    @Override
    public void setTitle(final String title) {
        frame.setTitle(title);
    }

    /// Places the container that clips the frame, relative to what holds it.
    ///
    /// @param left   Its left, in pixels.
    /// @param top    Its top, in pixels.
    /// @param width  Its width, in pixels.
    /// @param height Its height, in pixels.
    public void setContainerPositionAndSize(final double left,
                                            final double top,
                                            final double width,
                                            final double height) {
        setPositionAndSize(container.getElement(), "--vis-frame__container-", left, top, width, height);
    }

    /// Places the frame in its container.
    ///
    /// @param left   Its left, in pixels.
    /// @param top    Its top, in pixels.
    /// @param width  Its width, in pixels.
    /// @param height Its height, in pixels.
    public void setInnerPositionAndSize(final double left,
                                        final double top,
                                        final double width,
                                        final double height) {
        setPositionAndSize(frame.getElement(), "--vis-frame__frame-", left, top, width, height);
    }

    // Custom properties that vis-frame.css's vis-frame and vis-frame__frame use, named for
    // the part as they inherit into the frame
    private static void setPositionAndSize(final Element element,
                                           final String prefix,
                                           final double left,
                                           final double top,
                                           final double width,
                                           final double height) {
        CssProperties.set(element, prefix + "left", left + "px");
        CssProperties.set(element, prefix + "top", top + "px");
        CssProperties.set(element, prefix + "width", width + "px");
        CssProperties.set(element, prefix + "height", height + "px");
    }

    // Starts listening to the visualisation (its controller does, while it is in use)
    void bind() {
        messageSupport.bind();
    }

    // Stops listening to it, and hides its tooltip
    void unbind() {
        messageSupport.unbind();
    }

    /// Sets what is told when the user selects something in the visualisation, e.g. the selection
    /// model that other components filter on.
    ///
    /// @param onSelection Given what is selected.
    public void setOnSelection(final Consumer<List<ComponentSelection>> onSelection) {
        messageSupport.setOnSelection(onSelection);
    }

    /// Sets what opens a link the visualisation asks to follow (its controller sets it).
    ///
    /// @param onHyperlink Given the link.
    public void setOnHyperlink(final Consumer<LinkValue> onHyperlink) {
        messageSupport.setOnHyperlink(onHyperlink);
    }

    /// Sets what to do when the visualisation asks for buttons in its toolbar.
    ///
    /// @param toolbarHandler Given the buttons it wants, replacing any it asked for before.
    public void setToolbarHandler(final Consumer<List<VisButtonSpec>> toolbarHandler) {
        messageSupport.setToolbarHandler(toolbarHandler);
    }

    /// Sets what gives the visualisation the colours of its values when it asks for them.
    ///
    /// @param coloursHandler Given a field and some of its values, gives each value's colour.
    public void setColoursHandler(final BiFunction<String, List<String>, List<String>> coloursHandler) {
        messageSupport.setColoursHandler(coloursHandler);
    }

    /// Tells the visualisation which palette its values' colours come from, now and whenever it
    /// changes, so that it can ask for its colours again (`visualisationManager.coloursChanged` in
    /// `vis.js`).
    ///
    /// @param palette The palette's name, e.g. `Vivid`.
    public void coloursChanged(final String palette) {
        final JSONArray params = new JSONArray();
        params.set(0, new JSONString(palette));

        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.coloursChanged"));
        message.put("params", params);
        send(() -> messageSupport.postMessage(message));
    }

    /// Tells the visualisation the user's time zone and date-time pattern, when it starts and when
    /// they change, so that it shows times as the rest of Stroom does (`visualisationManager.timeChanged`
    /// in `vis.js`).
    ///
    /// @param settings The user's time settings.
    public void timeChanged(final DateTimeFormatter.VisTimeSettings settings) {
        final JSONObject time = new JSONObject();
        time.put("use", new JSONString(settings.getUse()));
        time.put("zoneId", settings.getZoneId() == null
                ? JSONNull.getInstance()
                : new JSONString(settings.getZoneId()));
        time.put("offsetMinutes", new JSONNumber(settings.getOffsetMinutes()));
        time.put("pattern", new JSONString(settings.getPattern()));
        time.put("compactPattern", new JSONString(settings.getCompactPattern()));
        final JSONArray params = new JSONArray();
        params.set(0, time);

        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.timeChanged"));
        message.put("params", params);
        send(() -> messageSupport.postMessage(message));
    }

    /// Tells the visualisation that one of the buttons it asked for has been pressed.
    ///
    /// @param id      The button's id.
    /// @param pressed A toggle's new state, or null for a button that isn't a toggle.
    public void toolbarButtonPressed(final String id, final Boolean pressed) {
        final JSONArray params = new JSONArray();
        params.set(0, new JSONString(id));
        params.set(1, pressed == null
                ? JSONNull.getInstance()
                : JSONBoolean.getInstance(pressed));

        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.toolbarButtonPressed"));
        message.put("params", params);
        send(() -> messageSupport.postMessage(message));
    }

    /// Loads a visualisation's scripts into the frame, telling the function when they have loaded
    /// or failed.
    ///
    /// @param scripts  The scripts, in the order they load.
    /// @param function The visualisation's function, told how its loading went.
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

        send(() -> messageSupport.postMessage(message, callback));
    }

    /// Sets the class on the visualisation's page, e.g. for the theme.
    ///
    /// @param className The class.
    public void setClassName(final String className) {
        final JSONArray params = new JSONArray();
        params.set(0, new JSONString(className));

        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.setClassName"));
        message.put("params", params);
        send(() -> messageSupport.postMessage(message));
    }

    /// Tells the visualisation that a search has started.
    public void start() {
        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.start"));
        send(() -> messageSupport.postMessage(message));
    }

    /// Tells the visualisation that the search has ended.
    public void end() {
        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.end"));
        send(() -> messageSupport.postMessage(message));
    }

    /// Tells the visualisation which of its types to draw.
    ///
    /// @param visType   The type's function name.
    /// @param className The class for its page, e.g. for the theme.
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

        send(() -> messageSupport.postMessage(message, callback));
    }

    /// Gives the visualisation its data to draw.
    ///
    /// @param context  The dashboard's context, e.g. its time range and parameters.
    /// @param settings The visualisation's settings.
    /// @param data     The data.
    public void setData(final JavaScriptObject context, final JavaScriptObject settings, final JavaScriptObject data) {
        final JSONArray params = new JSONArray();
        params.set(0, new JSONObject(context));
        params.set(1, new JSONObject(settings));
        params.set(2, new JSONObject(data));

        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.setData"));
        message.put("params", params);

        send(() -> messageSupport.postMessage(message));
    }

    @Override
    public void onResize() {
        final JSONObject message = new JSONObject();
        message.put("functionName", new JSONString("visualisationManager.resize"));
        send(() -> messageSupport.postMessage(message));
    }

    @Override
    public void removeFromParent() {
        // Do nothing...
    }

    /// @param loadHandler Told when the frame's page has loaded.
    /// @return The registration.
    public HandlerRegistration addLoadHandler(final LoadHandler loadHandler) {
        return frame.addLoadHandler(loadHandler);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, VisFrame> {

    }
}
