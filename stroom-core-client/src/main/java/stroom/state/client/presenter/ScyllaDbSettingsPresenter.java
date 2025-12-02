/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.state.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.state.client.presenter.ScyllaDbSettingsPresenter.ScyllaDbSettingsView;
import stroom.state.shared.ScyllaDbDoc;
import stroom.state.shared.ScyllaDbDocResource;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.UUID;

public class ScyllaDbSettingsPresenter
        extends DocumentEditPresenter<ScyllaDbSettingsView, ScyllaDbDoc>
        implements ScyllaDbSettingsUiHandlers {

    private static final ScyllaDbDocResource SCYLLA_DB_RESOURCE = GWT.create(ScyllaDbDocResource.class);

    private final RestFactory restFactory;

    @Inject
    public ScyllaDbSettingsPresenter(
            final EventBus eventBus,
            final ScyllaDbSettingsView view,
            final RestFactory restFactory) {
        super(eventBus, view);

        this.restFactory = restFactory;

        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
    }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    public void onTestConnection(final TaskMonitorFactory taskMonitorFactory) {
        final ScyllaDbDoc cluster = onWrite(ScyllaDbDoc.builder().uuid(UUID.randomUUID().toString()).build());
        restFactory
                .create(SCYLLA_DB_RESOURCE)
                .method(res -> res.testCluster(cluster))
                .onSuccess(result -> {
                    if (result.isOk()) {
                        AlertEvent.fireInfo(this, "Connection Success", result.getMessage(), null);
                    } else {
                        AlertEvent.fireError(this, "Connection Failure", result.getMessage(), null);
                    }
                })
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    protected void onRead(final DocRef docRef, final ScyllaDbDoc doc, final boolean readOnly) {
        getView().onReadOnly(readOnly);

        getView().setConnectionConfig(doc.getConnection());
        getView().setKeyspace(doc.getKeyspace());
        getView().setKeyspaceCql(doc.getKeyspaceCql());
    }

    @Override
    protected ScyllaDbDoc onWrite(final ScyllaDbDoc doc) {
        doc.setConnection(getView().getConnectionConfig());
        doc.setKeyspace(getView().getKeyspace());
        doc.setKeyspaceCql(getView().getKeyspaceCql());
        return doc;
    }

    public interface ScyllaDbSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<ScyllaDbSettingsUiHandlers> {

        String getConnectionConfig();

        void setConnectionConfig(String connectionConfig);

        String getKeyspace();

        void setKeyspace(String keyspace);

        String getKeyspaceCql();

        void setKeyspaceCql(String keyspaceCql);
    }
}
