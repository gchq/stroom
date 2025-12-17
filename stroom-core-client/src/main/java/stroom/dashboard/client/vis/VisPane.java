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
import stroom.visualisation.client.presenter.VisFunction;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.RequiresResize;
import com.gwtplatform.mvp.client.HasUiHandlers;

import java.util.List;

public interface VisPane extends IsWidget, RequiresResize, HasUiHandlers<SelectionUiHandlers> {

    void setClassName(String className);

    void injectScripts(List<ScriptDoc> scripts, VisFunction function);

    void start();

    void end();

    void setData(JavaScriptObject context, JavaScriptObject settings, JavaScriptObject data);

    void setVisType(String visType, String className);
}
