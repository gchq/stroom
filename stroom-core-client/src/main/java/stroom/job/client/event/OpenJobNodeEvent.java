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

package stroom.job.client.event;

import stroom.job.client.event.OpenJobNodeEvent.OpenJobNodeHandler;
import stroom.job.shared.JobNode;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class OpenJobNodeEvent extends GwtEvent<OpenJobNodeHandler> {

    private static Type<OpenJobNodeHandler> TYPE;
    private final JobNode jobNode;

    private OpenJobNodeEvent(final JobNode jobNode) {
        this.jobNode = jobNode;
    }

    /**
     * Opens a job node on the jobs screen
     */
    public static void fire(final HasHandlers handlers, final JobNode jobNode) {
        handlers.fireEvent(new OpenJobNodeEvent(jobNode));
    }

    public static Type<OpenJobNodeHandler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<OpenJobNodeHandler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final OpenJobNodeHandler handler) {
        handler.onOpen(this);
    }

    public JobNode getJobNode() {
        return jobNode;
    }

    @Override
    public String toString() {
        return "OpenJobNodeEvent{" +
                "jobNode=" + jobNode +
                '}';
    }

    // --------------------------------------------------------------------------------


    public interface OpenJobNodeHandler extends EventHandler {

        void onOpen(OpenJobNodeEvent event);
    }
}
