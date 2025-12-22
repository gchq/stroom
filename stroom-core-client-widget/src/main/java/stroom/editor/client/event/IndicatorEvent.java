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

package stroom.editor.client.event;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class IndicatorEvent extends GwtEvent<IndicatorEvent.IndicatorHandler> {

    public static final GwtEvent.Type<IndicatorHandler> TYPE = new GwtEvent.Type<>();
    private final int lineNo;
    private final Element target;

    protected IndicatorEvent(final Element target, final int lineNo) {
        this.lineNo = lineNo;
        this.target = target;
    }

    public static <I> void fire(final HasIndicatorHandlers source, final Element target, final int lineNo) {
        if (TYPE != null) {
            source.fireEvent(new IndicatorEvent(target, lineNo));
        }
    }

    @Override
    public com.google.gwt.event.shared.GwtEvent.Type<IndicatorHandler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final IndicatorHandler handler) {
        handler.onIndicator(this);
    }

    public int getLineNo() {
        return lineNo;
    }

    public Element getTarget() {
        return target;
    }

    public interface IndicatorHandler extends EventHandler {

        void onIndicator(IndicatorEvent event);
    }
}
