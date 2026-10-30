/*
 * Copyright 2026 Crown Copyright
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

package stroom.iframe.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.text.client.Frame;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.IFrameElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.stream.Collectors;

/// A frame showing a page: one at a URL, or HTML given as it is (e.g. rendered markdown), sandboxed
/// if asked. Its markup is `IFrame.ui.xml`.
public class IFrame extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String SOURCE_DOC_ATTRIBUTE = "srcdoc";
    private static final String SANDBOX_ATTRIBUTE = "sandbox";

    @UiField
    Frame frame;

    public IFrame() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param id The frame's id, e.g. for the page shown in it to be found.
    public void setId(final String id) {
        frame.getElement().setId(id);
    }

    /// @param url The page to show.
    public void setUrl(final String url) {
        frame.setUrl(url);
    }

    /// @param html The page to show, as HTML (its `srcdoc`).
    public void setSrcDoc(final String html) {
        frame.getElement().setAttribute(SOURCE_DOC_ATTRIBUTE, html);
    }

    /// Sandboxes the page, or not.
    ///
    /// @param isEnabled      Whether the page is sandboxed (it can run no scripts).
    /// @param sandboxOptions What a sandboxed page may still do, e.g. open popups so its links work.
    public void setSandboxEnabled(final boolean isEnabled, final SandboxOption... sandboxOptions) {
        final Element element = frame.getElement();
        if (isEnabled) {
            final String optionsStr = sandboxOptions == null
                    ? ""
                    : Arrays.stream(sandboxOptions)
                            .map(SandboxOption::getOption)
                            .collect(Collectors.joining(" "));
            element.setAttribute(SANDBOX_ATTRIBUTE, optionsStr);
        } else if (element.hasAttribute(SANDBOX_ATTRIBUTE)) {
            element.removeAttribute(SANDBOX_ATTRIBUTE);
        }
    }

    /// Names the frame for assistive technology, which announces a frame by its `title` (WCAG
    /// 4.1.2).
    ///
    /// @param name The frame's name, e.g. `Preview`.
    public void setAccessibleName(final String name) {
        frame.setTitle(name);
    }

    /// @return The title of the page shown, or null if it has none or it can't be read (a page from
    ///         elsewhere).
    public String getPageTitle() {
        final IFrameElement iFrameElement = frame.getElement().cast();
        try {
            return iFrameElement.getContentDocument().getTitle();
        } catch (final RuntimeException e) {
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    /// What a sandboxed page may still do: the values of the iframe's `sandbox` attribute, of which
    /// several may be used.
    public enum SandboxOption {
        /// Allows form submission
        ALLOW_FORMS("allow-forms"),
        /// Allows to open modal windows
        ALLOW_MODALS("allow-modals"),
        /// Allows to lock the screen orientation
        ALLOW_ORIENTATION_LOCK("allow-orientation-lock"),
        /// Allows to use the Pointer Lock API
        ALLOW_POINTER_LOCK("allow-pointer-lock"),
        /// Allows popups
        ALLOW_POPUPS("allow-popups"),
        /// Allows popups to open new windows without inheriting the sandboxing
        ALLOW_POPUPS_TO_ESCAPE_SANDBOX("allow-popups-to-escape-sandbox"),
        /// Allows to start a presentation session
        ALLOW_PRESENTATION("allow-presentation"),
        /// Allows the iframe content to be treated as being from the same origin
        ALLOW_SAME_ORIGIN("allow-same-origin"),
        /// Allows to run scripts
        ALLOW_SCRIPTS("allow-scripts"),
        /// Allows the iframe content to navigate its top-level browsing context
        ALLOW_TOP_NAVIGATION("allow-top-navigation"),
        /// Allows the iframe content to navigate its top-level browsing context, but only if initiated by
        /// the user
        ALLOW_TOP_NAVIGATION_BY_USER_ACTIVATION("allow-top-navigation-by-user-activation");

        private final String option;

        SandboxOption(final String option) {
            this.option = option;
        }

        /// @return The option as the `sandbox` attribute writes it, e.g. `allow-forms`.
        public String getOption() {
            return option;
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IFrame> {

    }
}
