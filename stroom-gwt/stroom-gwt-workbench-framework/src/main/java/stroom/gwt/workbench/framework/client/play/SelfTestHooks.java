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

package stroom.gwt.workbench.framework.client.play;

import com.google.gwt.dom.client.Element;

/// Exposes the browser side of the play API as `window.__workbenchDom`, so the self-test
/// (`stroom-gwt-workbench/test-runner/selftest`) can compare it with Testing Library, user-event
/// and jest-dom running in the same browser. The preview installs it only when its URL has a
/// `selftest` query parameter.
///
/// Each user event function returns null, or the message of the error it threw (as user-event
/// would throw), e.g. for `pointer-events: none`.
public final class SelfTestHooks {

    private SelfTestHooks() {
        // Static utility
    }

    /// Sets `window.__workbenchDom`.
    public static native void install() /*-{
        var helpers = @stroom.gwt.workbench.framework.client.play.Dom::helpers()();
        $wnd.__workbenchDom = {
            role: function (el) {
                return helpers.queryRole(el);
            },
            name: function (el) {
                return helpers.accessibleName(el);
            },
            isInaccessible: function (el) {
                return helpers.inaccessible(el);
            },
            nodeText: function (el) {
                return helpers.nodeText(el);
            },
            click: function (el) {
                return @stroom.gwt.workbench.framework.client.play.SelfTestHooks::click(*)(el, 1, 0, true);
            },
            dblClick: function (el) {
                return @stroom.gwt.workbench.framework.client.play.SelfTestHooks::click(*)(el, 2, 0, true);
            },
            tripleClick: function (el) {
                return @stroom.gwt.workbench.framework.client.play.SelfTestHooks::click(*)(el, 3, 0, true);
            },
            rightClick: function (el) {
                return @stroom.gwt.workbench.framework.client.play.SelfTestHooks::click(*)(el, 1, 2, false);
            },
            hover: function (el) {
                return @stroom.gwt.workbench.framework.client.play.SelfTestHooks::hover(*)(el, true);
            },
            unhover: function (el) {
                return @stroom.gwt.workbench.framework.client.play.SelfTestHooks::hover(*)(el, false);
            },
            type: function (el, text) {
                return @stroom.gwt.workbench.framework.client.play.SelfTestHooks::type(*)(el, text);
            },
            keyboard: function (text) {
                return @stroom.gwt.workbench.framework.client.play.SelfTestHooks::keyboard(*)($doc.body, text);
            },
            clear: function (el) {
                return @stroom.gwt.workbench.framework.client.play.SelfTestHooks::clear(*)(el);
            },
            fireEvent: function (el, method) {
                return @stroom.gwt.workbench.framework.client.play.SelfTestHooks::fireEvent(*)(el, method);
            },
            isVisible: function (el) {
                return @stroom.gwt.workbench.framework.client.play.Dom::isVisible(*)(el);
            },
            hasStyle: function (el, property, value) {
                return @stroom.gwt.workbench.framework.client.play.Dom::hasStyle(*)(el, property, value);
            },
            checkedState: function (el) {
                return @stroom.gwt.workbench.framework.client.play.Dom::getCheckedState(*)(el);
            },
            isDisabled: function (el) {
                return @stroom.gwt.workbench.framework.client.play.Dom::isDisabled(*)(el);
            }
        };
    }-*/;

    private static String click(final Element element, final int clickCount, final int button, final boolean move) {
        try {
            Dom.click(element, clickCount, button, 0, move);
            return null;
        } catch (final RuntimeException e) {
            return message(e);
        }
    }

    private static String hover(final Element element, final boolean over) {
        try {
            Dom.hover(element, over);
            return null;
        } catch (final RuntimeException e) {
            return message(e);
        }
    }

    private static String type(final Element element, final String text) {
        try {
            Play.typeInto(element, Keys.parse(text));
            return null;
        } catch (final RuntimeException e) {
            return message(e);
        }
    }

    private static String keyboard(final Element root, final String text) {
        try {
            Keyboard.press(root, Keys.parse(text), false);
            return null;
        } catch (final RuntimeException e) {
            return message(e);
        }
    }

    private static String clear(final Element element) {
        try {
            Dom.clear(element);
            return null;
        } catch (final RuntimeException e) {
            return message(e);
        }
    }

    private static String fireEvent(final Element element, final String method) {
        try {
            FireEvent.dispatch(element, method.toLowerCase(), null, element);
            return null;
        } catch (final RuntimeException e) {
            return message(e);
        }
    }

    private static String message(final RuntimeException e) {
        return e.getMessage() != null
                ? e.getMessage()
                : e.toString();
    }
}
