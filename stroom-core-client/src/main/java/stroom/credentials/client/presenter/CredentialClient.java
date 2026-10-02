/*
 * Copyright 2025 Crown Copyright
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

package stroom.credentials.client.presenter;

import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.CredentialsResource;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.credentials.shared.PutCredentialRequest;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.function.Consumer;

@Singleton
public class CredentialClient {

    public static final CredentialsResource CREDENTIALS_RESOURCE = GWT.create(CredentialsResource.class);

    private final RestFactory restFactory;

    @Inject
    public CredentialClient(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void findCredentialsWithPermissions(final FindCredentialRequest request,
                                final Consumer<ResultPage<CredentialWithPerms>> dataConsumer,
                                final RestErrorHandler restErrorHandler,
                                final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(CREDENTIALS_RESOURCE)
                .method(r -> r.findCredentialsWithPermissions(request))
                .onSuccess(dataConsumer)
                .onFailure(restErrorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void findCredentials(final FindCredentialRequest request,
                                final Consumer<ResultPage<Credential>> dataConsumer,
                                final RestErrorHandler restErrorHandler,
                                final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(CREDENTIALS_RESOURCE)
                .method(r -> r.findCredentials(request))
                .onSuccess(dataConsumer)
                .onFailure(restErrorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void getCredentialByUuid(final String uuid,
                                    final Consumer<Credential> consumer,
                                    final TaskMonitorFactory taskMonitorFactory) {
        restFactory.create(CREDENTIALS_RESOURCE)
                .method(res -> res.getCredentialByUuid(uuid))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void getCredentialByName(final String name,
                                    final Consumer<Credential> consumer,
                                    final TaskMonitorFactory taskMonitorFactory) {
        restFactory.create(CREDENTIALS_RESOURCE)
                .method(res -> res.getCredentialByName(name))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void createDocRef(final Consumer<DocRef> consumer,
                             final TaskMonitorFactory taskMonitorFactory) {
        restFactory.create(CREDENTIALS_RESOURCE)
                .method(CredentialsResource::createDocRef)
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void storeCredential(final PutCredentialRequest request,
                      final Consumer<Credential> consumer,
                      final RestErrorHandler restErrorHandler,
                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory.create(CREDENTIALS_RESOURCE)
                .method(res ->
                        res.storeCredential(request))
                .onSuccess(consumer)
                .onFailure(restErrorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void deleteCredentials(final String uuid,
                       final Consumer<Boolean> consumer,
                       final TaskMonitorFactory taskMonitorFactory) {
        restFactory.create(CREDENTIALS_RESOURCE)
                .method(res -> res.deleteCredentials(uuid))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
