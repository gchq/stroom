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

import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import stroom.dashboard.client.vis.MyScriptInjector.FromString;
import stroom.dashboard.client.vis.MyScriptInjector.FromUrl;
import stroom.script.shared.ScriptDoc;
import stroom.visualisation.client.presenter.VisFunction;
import stroom.visualisation.client.presenter.VisFunction.LoadStatus;

import java.util.List;
import java.util.Set;

public class VisPanel extends SimplePanel implements VisPane {
    private final Set<String> loadedScripts;
    private JavaScriptObject vis;
    private boolean autoResize = true;

    public VisPanel(final Set<String> loadedScripts) {
        this.loadedScripts = loadedScripts;
    }

    @Override
    public void setVisType(final String visType) {
        vis = null;
        vis = create(visType);
        final Element visElement = getVisElement(vis);
        final Wrapper wrapper = new Wrapper(visElement);
        setWidget(wrapper);
    }

    @Override
    public void injectScripts(final List<ScriptDoc> scripts, final VisFunction function) {
        injectScriptsFromURL(scripts, function);
    }

    private void injectScriptDirectly(final List<ScriptDoc> scripts, final VisFunction function) {
        if (scripts == null || scripts.size() == 0) {
            // Set the function status to loaded. This will tell all handlers
            // that the function is ready for use.
            if (!LoadStatus.FAILURE.equals(function.getStatus())) {
                function.setStatus(LoadStatus.LOADED);
            }

        } else {
            // Only load script if we haven't already had a failure.
            if (!LoadStatus.FAILURE.equals(function.getStatus())) {
                // Get the next script to load.
                final ScriptDoc script = scripts.remove(0);

                // Make sure we don't inject a script more than once.
                if (!loadedScripts.contains(script.getUuid())) {
                    // Remember that we have loaded this script so we don't try
                    // and fetch it again.
                    loadedScripts.add(script.getUuid());

                    if (script.getData() != null) {
                        try {
                            final String text = script.getData();
                            final FromString fromString = fromString(text);
                            fromString.inject();

                            // Inject the next script in the list.
                            injectScriptDirectly(scripts, function);

                        } catch (final RuntimeException e) {
                            failure(function, "Failed to inject script '" + script.getName() + "' - " + e.getMessage());
                        }
                    } else {
                        // Inject the next script in the list.
                        injectScriptDirectly(scripts, function);
                    }

                } else {
                    // Inject the next script in the list.
                    injectScriptDirectly(scripts, function);
                }
            }
        }
    }

    private void injectScriptsFromString(final List<ScriptDoc> scripts, final VisFunction function) {
        if (scripts == null || scripts.size() == 0) {
            // Set the function status to loaded. This will tell all handlers
            // that the function is ready for use.
            if (!LoadStatus.FAILURE.equals(function.getStatus())) {
                function.setStatus(LoadStatus.LOADED);
            }

        } else {
            // Only load script if we haven't already had a failure.
            if (!LoadStatus.FAILURE.equals(function.getStatus())) {
                // Get the next script to load.
                final ScriptDoc script = scripts.remove(0);

                // Make sure we don't inject a script more than once.
                if (!loadedScripts.contains(script.getUuid())) {
                    final RequestCallback requestCallback = new RequestCallback() {
                        @Override
                        public void onResponseReceived(final Request request, final Response response) {
                            // Make sure we don't inject a script more than
                            // once.
                            if (!loadedScripts.contains(script.getUuid())) {
                                try {
                                    final String text = response.getText();

                                    if (response.getStatusCode() == 200) {
                                        final FromString fromString = fromString(text);
                                        fromString.inject();

                                        // Inject the next script in the list.
                                        injectScriptsFromString(scripts, function);

                                    } else {
                                        failure(function, "Failed to inject script '" + script.getName() + "' - Status "
                                                + response.getStatusCode());
                                    }
                                } catch (final RuntimeException e) {
                                    failure(function,
                                            "Failed to inject script '" + script.getName() + "' - " + e.getMessage());
                                } finally {
                                    // Remember that we have loaded or at least
                                    // attempted to load this script so we don't
                                    // try and fetch it again.
                                    loadedScripts.add(script.getUuid());
                                }
                            } else {
                                // Inject the next script in the list.
                                injectScriptsFromString(scripts, function);
                            }
                        }

                        @Override
                        public void onError(final Request request, final Throwable e) {
                            failure(function, "Failed to inject script '" + script.getName() + "' - " + e.getMessage());
                        }
                    };

                    try {
                        final String url = createURL(script);
                        final RequestBuilder requestBuilder = new RequestBuilder(RequestBuilder.GET, url);
                        requestBuilder.setCallback(requestCallback);
                        requestBuilder.send();
                    } catch (final RequestException | RuntimeException e) {
                        failure(function, "Failed to inject script '" + script.getName() + "' - " + e.getMessage());
                    }

                } else {
                    // Inject the next script in the list.
                    injectScriptsFromString(scripts, function);
                }
            }
        }
    }

    private void injectScriptsFromURL(final List<ScriptDoc> scripts, final VisFunction function) {
        if (scripts == null || scripts.size() == 0) {
            // Set the function status to loaded. This will tell all handlers
            // that the function is ready for use.
            if (!LoadStatus.FAILURE.equals(function.getStatus())) {
                function.setStatus(LoadStatus.LOADED);
            }

        } else {
            // Only load script if we haven't already had a failure.
            if (!LoadStatus.FAILURE.equals(function.getStatus())) {
                // Get the next script to load.
                final ScriptDoc script = scripts.remove(0);

                // Make sure we don't inject a script more than once.
                if (!loadedScripts.contains(script.getUuid())) {
                    final Callback<Void, Exception> callback = new Callback<Void, Exception>() {
                        @Override
                        public void onSuccess(final Void result) {
                            // Remember that we have loaded or at least
                            // attempted to load this script so we don't try and
                            // fetch it again.
                            loadedScripts.add(script.getUuid());
                            // Inject the next script in the list.
                            injectScriptsFromURL(scripts, function);
                        }

                        @Override
                        public void onFailure(final Exception e) {
                            // Remember that we have loaded or at least
                            // attempted to load this script so we don't try and
                            // fetch it again.
                            loadedScripts.add(script.getUuid());
                            // Show failure message.
                            failure(function,
                                    "Failed to inject script '" + script.getName() + "' - " + e.getMessage());
                        }
                    };

                    final String url = createURL(script);

                    final FromUrl fromURL = fromUrl(url);
                    fromURL.setCallback(callback);
                    fromURL.inject();

                } else {
                    // Inject the next script in the list.
                    injectScriptsFromURL(scripts, function);
                }
            }
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

    private FromUrl fromUrl(final String url) {
        final FromUrl fromURL = MyScriptInjector.fromUrl(url);
        // fromURL.setRemoveTag(false);
        return fromURL;
    }

    private FromString fromString(final String string) {
        final FromString fromString = MyScriptInjector.fromString(string);
        // fromString.setRemoveTag(false);
        return fromString;
    }

    private void failure(final VisFunction function, final String message) {
        if (!LoadStatus.FAILURE.equals(function.getStatus())) {
            function.setStatus(LoadStatus.FAILURE, message);
        }
    }

    @Override
    public void start() {
        start(vis);
    }

    @Override
    public void end() {
        end(vis);
    }

    @Override
    public void setData(final JavaScriptObject context, final JavaScriptObject settings, final JavaScriptObject data) {
        if (vis != null) {
            setData(vis, context, settings, data);
        }
    }

    public void resize() {
        if (vis != null) {
            resize(vis);
        }
    }

    @Override
    public void onResize() {
        if (autoResize) {
            resize(vis);
        }
    }

    public void setAutoResize(final boolean autoResize) {
        this.autoResize = autoResize;
    }

    private final native JavaScriptObject create(final String type)
    /*-{
    var vis = eval("new " + type + "()");
    return vis;
    }-*/;

    private final native Element getVisElement(final JavaScriptObject vis)
    /*-{
    return vis.element;
    }-*/;

    private final native void start(final JavaScriptObject vis)
    /*-{
    if (vis && vis.start) {
        vis.start();
    }
    }-*/;

    private final native void end(final JavaScriptObject vis)
    /*-{
    if (vis && vis.end) {
        vis.end();
    }
    }-*/;

    private final native void setData(final JavaScriptObject vis, final JavaScriptObject context,
                                      final JavaScriptObject settings, final JavaScriptObject data)
            /*-{
            vis.setData(context, settings, data);
            }-*/;

    private final native void resize(final JavaScriptObject vis)
    /*-{
    vis.resize();
    }-*/;

    private class Wrapper extends Widget {
        public Wrapper(final Element elem) {
            setElement(elem);
        }
    }
}
