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

package stroom.dashboard.client.query;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ProcessorLimitsPresenter extends MyPresenterWidget<ProcessorLimitsPresenter.ProcessorLimitsView> {

    @Inject
    public ProcessorLimitsPresenter(final EventBus eventBus, final ProcessorLimitsView view) {
        super(eventBus, view);
    }

    public Long getTimeLimitMins() {
        return getView().getTimeLimitMins();
    }

    public void setTimeLimitMins(final Long value) {
        getView().setTimeLimitMins(value);
    }

    public Long getRecordLimit() {
        return getView().getRecordLimit();
    }

    public void setRecordLimit(final Long value) {
        getView().setRecordLimit(value);
    }

    public interface ProcessorLimitsView extends View, Focus {

        Long getTimeLimitMins();

        void setTimeLimitMins(Long value);

        Long getRecordLimit();

        void setRecordLimit(Long value);
    }
}
