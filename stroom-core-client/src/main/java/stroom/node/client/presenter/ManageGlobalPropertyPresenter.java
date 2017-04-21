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

package stroom.node.client.presenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.client.presenter.ManageEntityPresenter;
import stroom.entity.shared.StringCriteria.MatchStyle;
import stroom.node.shared.FindGlobalPropertyCriteria;
import stroom.node.shared.GlobalProperty;
import stroom.security.client.ClientSecurityContext;

public class ManageGlobalPropertyPresenter extends ManageEntityPresenter<FindGlobalPropertyCriteria, GlobalProperty> {
    private final ManageGlobalPropertyListPresenter manageGlobalPropertyListPresenter;

    @Inject
    public ManageGlobalPropertyPresenter(final EventBus eventBus, final ManageEntityView view,
            final ManageGlobalPropertyListPresenter listPresenter,
            final Provider<ManageGlobalPropertyEditPresenter> editProvider,
            final ClientDispatchAsync dispatcher, final ClientSecurityContext securityContext) {
        super(eventBus, view, listPresenter, editProvider, null, dispatcher, securityContext);
        this.manageGlobalPropertyListPresenter = listPresenter;
    }

    @Override
    protected String getEntityType() {
        return GlobalProperty.ENTITY_TYPE;
    }

    @Override
    protected String getEntityDisplayType() {
        return "System Property";
    }

    @Override
    protected GlobalProperty newEntity() {
        return null;
    }

    @Override
    public void changeNameFilter(final String name) {
        if (name.length() > 0) {
            manageGlobalPropertyListPresenter.getFindGlobalPropertyCriteria().getName().setString(name);
            manageGlobalPropertyListPresenter.getFindGlobalPropertyCriteria().getName()
                    .setMatchStyle(MatchStyle.WildStandAndEnd);
            manageGlobalPropertyListPresenter.getFindGlobalPropertyCriteria().getName().setCaseInsensitive(true);
            manageGlobalPropertyListPresenter.refresh();
        } else {
            manageGlobalPropertyListPresenter.getFindGlobalPropertyCriteria().getName().clear();
            manageGlobalPropertyListPresenter.refresh();
        }
    }

    @Override
    protected boolean allowDelete() {
        return false;
    }

    @Override
    protected boolean allowNew() {
        return false;
    }
}
