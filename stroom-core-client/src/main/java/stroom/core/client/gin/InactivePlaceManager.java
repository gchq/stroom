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

package stroom.core.client.gin;

import com.google.gwt.event.shared.GwtEvent;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.PlaceRequest;
import com.gwtplatform.mvp.client.proxy.SetPlaceTitleHandler;

import java.util.List;

public class InactivePlaceManager implements PlaceManager {

    @Override
    public void fireEvent(final GwtEvent<?> event) {
    }

    @Override
    public String buildHistoryToken(final PlaceRequest request) {
        return null;
    }

    @Override
    public String buildRelativeHistoryToken(final int level) {
        return null;
    }

    @Override
    public String buildRelativeHistoryToken(final PlaceRequest request) {
        return null;
    }

    @Override
    public String buildRelativeHistoryToken(final PlaceRequest request, final int level) {
        return null;
    }

    @Override
    public List<PlaceRequest> getCurrentPlaceHierarchy() {
        return null;
    }

    @Override
    public PlaceRequest getCurrentPlaceRequest() {
        return null;
    }

    @Override
    public void getTitle(final int index, final SetPlaceTitleHandler handler) throws IndexOutOfBoundsException {
    }

    @Override
    public void getCurrentTitle(final SetPlaceTitleHandler handler) {
    }

    @Override
    public EventBus getEventBus() {
        return null;
    }

    @Override
    public int getHierarchyDepth() {
        return 0;
    }

    @Override
    public void navigateBack() {
    }

    @Override
    public void updateHistory(final PlaceRequest request, final boolean updateBrowserUrl) {
    }

    @Override
    public void revealCurrentPlace() {
    }

    @Override
    public void revealDefaultPlace() {
    }

    @Override
    public void revealErrorPlace(final String invalidHistoryToken) {
    }

    @Override
    public void revealUnauthorizedPlace(final String unauthorizedHistoryToken) {
    }

    @Override
    public void setOnLeaveConfirmation(final String question) {
    }

    @Override
    public void revealPlace(final PlaceRequest request) {
    }

    @Override
    public void revealPlace(final PlaceRequest request, final boolean updateBrowserUrl) {
    }

    @Override
    public void revealPlaceHierarchy(final List<PlaceRequest> placeRequestHierarchy) {
    }

    @Override
    public void revealRelativePlace(final int level) {
    }

    @Override
    public void revealRelativePlace(final PlaceRequest request) {
    }

    @Override
    public void revealRelativePlace(final PlaceRequest request, final int level) {
    }

    @Override
    public void unlock() {
    }

    @Override
    public boolean hasPendingNavigation() {
        return false;
    }

}
