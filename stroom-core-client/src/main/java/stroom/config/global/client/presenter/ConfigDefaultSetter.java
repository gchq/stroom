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

package stroom.config.global.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.config.global.shared.ConfigTarget;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.SetConfigValueRequest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Promotes a value the user has just chosen to the default for everyone, from the screen where that value is
 * edited, rather than making them find the property in the global properties screen.
 * <p>
 * This writes a system wide property, which is a different thing from setting the field on the document in front
 * of the user, so it always confirms first.
 * </p>
 */
@Singleton
public class ConfigDefaultSetter {

    private static final GlobalConfigResource CONFIG_RESOURCE = GWT.create(GlobalConfigResource.class);

    private final RestFactory restFactory;
    private final ClientSecurityContext clientSecurityContext;
    private final UiConfigCache uiConfigCache;

    @Inject
    public ConfigDefaultSetter(final RestFactory restFactory,
                               final ClientSecurityContext clientSecurityContext,
                               final UiConfigCache uiConfigCache) {
        this.restFactory = restFactory;
        this.clientSecurityContext = clientSecurityContext;
        this.uiConfigCache = uiConfigCache;
    }

    /**
     * @return True if the current user is allowed to change global properties. Callers should hide their
     * 'set default' control when this is false, as the server will refuse the change.
     */
    public boolean isAllowed() {
        return clientSecurityContext.hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION);
    }

    /**
     * @param description How to describe the default in the confirmation and the result, e.g. "error feed".
     */
    public void setDefault(final HasHandlers hasHandlers,
                           final ConfigTarget target,
                           final String propertyName,
                           final DocRef value,
                           final String description,
                           final TaskMonitorFactory taskMonitorFactory) {
        if (value == null) {
            AlertEvent.fireWarn(hasHandlers, "Choose a " + description + " before setting it as the default.", null);
        } else {
            setDefault(hasHandlers,
                    SetConfigValueRequest.docRef(target, propertyName, value),
                    description,
                    value.getName(),
                    taskMonitorFactory);
        }
    }

    public void setDefault(final HasHandlers hasHandlers,
                           final ConfigTarget target,
                           final String propertyName,
                           final String value,
                           final String description,
                           final TaskMonitorFactory taskMonitorFactory) {
        if (value == null || value.trim().isEmpty()) {
            AlertEvent.fireWarn(hasHandlers, "Choose a " + description + " before setting it as the default.", null);
        } else {
            setDefault(hasHandlers,
                    SetConfigValueRequest.string(target, propertyName, value),
                    description,
                    value,
                    taskMonitorFactory);
        }
    }

    private void setDefault(final HasHandlers hasHandlers,
                            final SetConfigValueRequest request,
                            final String description,
                            final String valueName,
                            final TaskMonitorFactory taskMonitorFactory) {
        ConfirmEvent.fire(
                hasHandlers,
                "Set '" + valueName + "' as the default " + description + " for all users?",
                ok -> {
                    if (ok) {
                        restFactory
                                .create(CONFIG_RESOURCE)
                                .method(res -> res.setConfigValue(request))
                                .onSuccess(success -> {
                                    // Refresh the cached config, else this session keeps showing the old default.
                                    uiConfigCache.refresh(ignored -> {
                                    }, taskMonitorFactory);
                                    AlertEvent.fireInfo(
                                            hasHandlers,
                                            "The default " + description + " is now '" + valueName + "'.",
                                            null);
                                })
                                .taskMonitorFactory(taskMonitorFactory)
                                .exec();
                    }
                });
    }
}
