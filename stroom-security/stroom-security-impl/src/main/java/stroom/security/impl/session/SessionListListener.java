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
 */

package stroom.security.impl.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.UserIdentity;
import stroom.util.servlet.UserAgentSessionUtil;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.ResultList;

import javax.inject.Singleton;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
class SessionListListener implements HttpSessionListener, SessionListService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionListListener.class);

    private final ConcurrentHashMap<String, HttpSession> sessionMap = new ConcurrentHashMap<>();

    @Override
    public void sessionCreated(final HttpSessionEvent event) {
        final HttpSession httpSession = event.getSession();
        LOGGER.info("sessionCreated() - {}", httpSession.getId());
        sessionMap.put(httpSession.getId(), httpSession);
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent event) {
        final HttpSession httpSession = event.getSession();
        LOGGER.info("sessionDestroyed() - {}", httpSession.getId());
        sessionMap.remove(httpSession.getId());
    }

    @Override
    public ResultList<SessionDetails> find(final BaseCriteria criteria) {
        final ArrayList<SessionDetails> rtn = new ArrayList<>();
        for (final HttpSession httpSession : sessionMap.values()) {
            final SessionDetails sessionDetails = new SessionDetails();

            final UserIdentity userIdentity = UserIdentitySessionUtil.get(httpSession);
            if (userIdentity != null) {
                sessionDetails.setUserName(userIdentity.getId());
            }

            sessionDetails.setCreateMs(httpSession.getCreationTime());
            sessionDetails.setLastAccessedMs(httpSession.getLastAccessedTime());
            sessionDetails.setLastAccessedAgent(UserAgentSessionUtil.get(httpSession));

            rtn.add(sessionDetails);
        }
        return ResultList.createUnboundedList(rtn);
    }

    @Override
    public BaseCriteria createCriteria() {
        return null;
    }
}
