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

package stroom.dashboard.client.main;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsonUtils;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.autobean.shared.AutoBean;
import com.google.web.bindery.autobean.shared.AutoBeanCodex;
import com.google.web.bindery.autobean.shared.AutoBeanUtils;
import com.google.web.bindery.event.shared.EventBus;
import stroom.dashboard.shared.DashboardQueryKey;
import stroom.dashboard.shared.SearchBusPollAction;
import stroom.dashboard.shared.SearchBusPollResult;
import stroom.dashboard.shared.SearchRequest;
import stroom.dashboard.shared.SearchResponse;
import stroom.dispatch.client.AsyncCallbackAdaptor;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.dispatch.client.RestService;
import stroom.security.client.event.LogoutEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@Singleton
public class SearchBus {
    private static final int ONE_SECOND = 1000;
    private static final int DEFAULT_POLL_INTERVAL = ONE_SECOND;
    private static final int QUICK_POLL_INTERVAL = 10;

    private final ClientDispatchAsync dispatcher;
    private final RestService restService;

    private final Map<DashboardQueryKey, SearchModel> activeSearchMap = new HashMap<>();
    private final Timer pollingTimer;
    private int delayMillis = DEFAULT_POLL_INTERVAL;
    private boolean forcePoll;
    private boolean polling;

    @Inject
    public SearchBus(final EventBus eventBus, final ClientDispatchAsync dispatcher, final RestService restService) {
        this.dispatcher = dispatcher;
        this.restService = restService;

        pollingTimer = new Timer() {
            @Override
            public void run() {
                doPoll();
            }
        };

        // Listen for logout events.
        eventBus.addHandler(LogoutEvent.getType(), event -> reset());
    }

    private void reset() {
        activeSearchMap.clear();
        delayMillis = DEFAULT_POLL_INTERVAL;
        forcePoll = false;
        polling = false;
        pollingTimer.cancel();
    }

    public void put(final DashboardQueryKey queryKey, final SearchModel searchModel) {
        activeSearchMap.put(queryKey, searchModel);
    }

    public void remove(final DashboardQueryKey queryKey) {
        activeSearchMap.remove(queryKey);
    }

    public void poll() {
        if (polling) {
            delayMillis = QUICK_POLL_INTERVAL;
            forcePoll = true;
        } else {
            poll(QUICK_POLL_INTERVAL);
        }
    }

    private void poll(final int delayMillis) {
        pollingTimer.schedule(delayMillis);
    }

    private void doPoll() {
        polling = true;
        final Map<DashboardQueryKey, SearchRequest> searchActionMap = new HashMap<>();
        for (final Entry<DashboardQueryKey, SearchModel> entry : activeSearchMap.entrySet()) {
            final DashboardQueryKey queryKey = entry.getKey();
            final SearchModel searchModel = entry.getValue();
            final SearchRequest searchAction = searchModel.getRequest();
            searchActionMap.put(queryKey, searchAction);
        }

//        final JsDocRef jsDocRef = JavaScriptObject.createObject().cast();
//        jsDocRef.setName("test");
//        jsDocRef.setType("typew");
//        jsDocRef.setUuid("uuid");
//
//        final JsExpressionOperator innerOp1 = JavaScriptObject.createObject().cast();
//        final JsExpressionOperator innerOp2 = JavaScriptObject.createObject().cast();
//        final JsExpressionOperator expression = JavaScriptObject.createObject().cast();
//        final JsExpressionItem[] array = (JsExpressionItem[]) (Object) JavaScriptObject.createArray(2).cast();
//        array[0] = innerOp1;
//        array[1] = innerOp2;
//
//        expression.setChildren(array);
//
//        final JsSearch jsSearch = JavaScriptObject.createObject().cast();
//        jsSearch.setDataSourceRef(jsDocRef);
//        jsSearch.setExpression(expression);
//
//
//        String json = JsonUtils.encode(jsSearch);
//
//        final Response response = restService.send("rest/dashboard/poll", json);
//        response.onSuccess(GWT::log);


        final SearchBusPollAction action = new SearchBusPollAction(searchActionMap);
        dispatcher.execute(action, false, new AsyncCallbackAdaptor<SearchBusPollResult>() {
            @Override
            public void onSuccess(final SearchBusPollResult result) {
                final Map<DashboardQueryKey, SearchResponse> searchResultMap = result.getSearchResultMap();
                for (final Entry<DashboardQueryKey, SearchResponse> entry : searchResultMap.entrySet()) {
                    final DashboardQueryKey queryKey = entry.getKey();
                    final SearchResponse searchResponse = entry.getValue();
                    final SearchModel searchModel = activeSearchMap.get(queryKey);
                    if (searchModel != null) {
                        searchModel.update(searchResponse);
                    }
                }

                polling = false;

                // Remember and reset delay.
                final int delay = delayMillis;
                delayMillis = DEFAULT_POLL_INTERVAL;

                if (forcePoll || activeSearchMap.size() > 0) {
                    // Reset force.
                    forcePoll = false;
                    poll(delay);
                }
            }
        });
    }
}
