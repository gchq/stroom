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

package stroom.streamstore.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.IncludeExcludeEntityIdSet;

public class IncludeExcludeEntityIdSetPopupPresenter
        extends MyPresenterWidget<IncludeExcludeEntityIdSetPopupPresenter.IncludeExcludeEntityIdSetPopupView> {
    public interface IncludeExcludeEntityIdSetPopupView extends View {
        void setIncludesView(View view);

        void setExcludesView(View view);
    }

    private final EntityIdSetPresenter includes;
    private final EntityIdSetPresenter excludes;

    @Inject
    public IncludeExcludeEntityIdSetPopupPresenter(final EventBus eventBus,
            final IncludeExcludeEntityIdSetPopupView view, final EntityIdSetPresenter includes,
            final EntityIdSetPresenter excludes) {
        super(eventBus, view);
        this.includes = includes;
        this.excludes = excludes;

        view.setIncludesView(includes.getView());
        view.setExcludesView(excludes.getView());
    }

    public <T extends BaseEntity> void read(final String type, final boolean groupedEntity,
            final IncludeExcludeEntityIdSet<T> includeExcludeEntityIdSet) {
        EntityIdSet<T> in = null;
        EntityIdSet<T> ex = null;

        if (includeExcludeEntityIdSet != null) {
            in = includeExcludeEntityIdSet.getInclude();
            ex = includeExcludeEntityIdSet.getExclude();
        }

        includes.read(type, groupedEntity, in);
        excludes.read(type, groupedEntity, ex);
    }

    public <T extends BaseEntity> void write(final IncludeExcludeEntityIdSet<T> includeExcludeEntityIdSet) {
        includes.write(includeExcludeEntityIdSet.obtainInclude());
        excludes.write(includeExcludeEntityIdSet.obtainExclude());

        if (!includeExcludeEntityIdSet.obtainInclude().isConstrained()) {
            includeExcludeEntityIdSet.setInclude(null);
        }

        if (!includeExcludeEntityIdSet.obtainExclude().isConstrained()) {
            includeExcludeEntityIdSet.setExclude(null);
        }
    }
}
