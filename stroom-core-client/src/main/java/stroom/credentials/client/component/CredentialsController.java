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

package stroom.credentials.client.component;

import stroom.content.client.component.ContentTabController;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows the stored credentials.
public class CredentialsController extends ContentTabController {

    public static final String TAB_TYPE = "Credentials";
    private static final String LABEL = "Credentials";

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus        The application's event bus.
    /// @param credentialsList The credentials list.
    @Inject
    public CredentialsController(final EventBus eventBus,
                                 final CredentialsListController credentialsList) {
        super(eventBus);
        own(credentialsList);
        initWidget(credentialsList.asWidget());
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.KEY_BLUE;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return LABEL;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
