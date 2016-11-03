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

package stroom.widget.tab.client.view;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.DOM;

import stroom.util.shared.EqualsUtil;
import stroom.widget.tab.client.presenter.TabData;

public class SlideTabBar extends AbstractTabBar {
    public interface Style extends CssResource {
        String slideTabBar();

        String underline();

        String transition();
    }

    public interface Resources extends ClientBundle {
        @Source("SlideTabBar.css")
        Style style();
    }

    private static Resources resources;

    private final Element element;
    private final Element underline;
    private boolean transitionEnabled;

    public SlideTabBar() {
        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        element = DOM.createDiv();
        element.setClassName(resources.style().slideTabBar());

        underline = DOM.createDiv();
        underline.setClassName(resources.style().underline());
        element.appendChild(underline);

        setElement(element);
    }

    @Override
    protected AbstractTab createTab(final TabData tabData) {
        return new SlideTab(tabData.getLabel(), tabData.isCloseable());
    }

    @Override
    protected AbstractTabSelector createTabSelector() {
        final BasicTabSelector tabSelector = new BasicTabSelector();
        tabSelector.getElement().getStyle().setPaddingLeft(4, Unit.PX);
        tabSelector.getElement().getStyle().setPaddingTop(4, Unit.PX);
        return tabSelector;
    }

    @Override
    public void selectTab(final TabData tabData) {
        if (!EqualsUtil.isEquals(tabData, getSelectedTab())) {
            super.selectTab(tabData);

            // If we are animating the transition then we need to do defer this
            // as otherwise the animation is stalled by other processing.
            Scheduler.get().scheduleDeferred(new ScheduledCommand() {
                @Override
                public void execute() {
                    moveUnderline();
                }
            });
        }
    }

    private void moveUnderline() {
        if (getSelectedTab() != null) {
            final AbstractTab tab = getTab(getSelectedTab());
            underline.getStyle().setLeft(tab.getElement().getOffsetLeft(), Unit.PX);
            underline.getStyle().setWidth(tab.getElement().getOffsetWidth(), Unit.PX);

            // All subsequent selections should use a transition.
            setTransitionEnabled(true);

        } else {
            underline.getStyle().setLeft(0, Unit.PX);
            underline.getStyle().setWidth(0, Unit.PX);

            // The first selection after a null selection should not use
            // transitions.
            setTransitionEnabled(false);
        }
    }

    private void setTransitionEnabled(final boolean enabled) {
        if (enabled != transitionEnabled) {
            transitionEnabled = enabled;
            Scheduler.get().scheduleDeferred(new ScheduledCommand() {
                @Override
                public void execute() {
                    if (transitionEnabled) {
                        underline.addClassName(resources.style().transition());
                    } else {
                        underline.removeClassName(resources.style().transition());
                    }
                }
            });
        }
    }

    @Override
    public void onResize() {
        super.onResize();
        moveUnderline();
    }
}
