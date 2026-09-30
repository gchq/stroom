/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.identity.token;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.security.identity.shared.SigningKeyResource;
import stroom.security.identity.shared.SigningKeyRow;
import stroom.security.identity.shared.SigningKeyStatus;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import event.logging.AuthenticateAction;
import event.logging.AuthenticateEventAction;
import event.logging.AuthenticateOutcome;
import event.logging.Data;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Objects;

// revoke() and revokeAll() write their own event in afterRevoke(), which knows the thing worth recording -
// whether the key that signs new tokens was among those withdrawn.
@AutoLogged(OperationType.MANUALLY_LOGGED)
public class SigningKeyResourceImpl implements SigningKeyResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SigningKeyResourceImpl.class);

    private final Provider<JwkDao> jwkDaoProvider;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<SigningKeyRefreshFanOut> signingKeyRefreshFanOutProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    public SigningKeyResourceImpl(final Provider<JwkDao> jwkDaoProvider,
                                  final Provider<SecurityContext> securityContextProvider,
                                  final Provider<SigningKeyRefreshFanOut> signingKeyRefreshFanOutProvider,
                                  final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.jwkDaoProvider = jwkDaoProvider;
        this.securityContextProvider = securityContextProvider;
        this.signingKeyRefreshFanOutProvider = signingKeyRefreshFanOutProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public List<SigningKeyRow> list() {
        return securityContextProvider.get().secureResult(
                AppPermission.ADMINISTRATOR,
                () -> jwkDaoProvider.get().list());
    }

    @Override
    public Integer revoke(final Integer id) {
        Objects.requireNonNull(id, "id required");
        return securityContextProvider.get().secureResult(AppPermission.ADMINISTRATOR, () -> {
            // Read the key's state before withdrawing it, so the audit can say whether the one that signs
            // new tokens was among them - which is the difference between signing out a few stragglers and
            // signing out everybody.
            final boolean wasActive = jwkDaoProvider.get().list().stream()
                    .anyMatch(row -> row.getId() == id && row.getStatus() == SigningKeyStatus.ACTIVE);

            final int revoked = jwkDaoProvider.get().revoke(
                    id, securityContextProvider.get().getUserIdentityForAudit());
            afterRevoke(revoked, wasActive, "one signing key");
            return revoked;
        });
    }

    @Override
    public Integer revokeAll() {
        return securityContextProvider.get().secureResult(AppPermission.ADMINISTRATOR, () -> {
            final int revoked = jwkDaoProvider.get().revokeAll(
                    securityContextProvider.get().getUserIdentityForAudit());
            // Revoking everything necessarily includes whatever was signing.
            afterRevoke(revoked, revoked > 0, "all signing keys");
            return revoked;
        });
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public Boolean refreshOnNode(final String nodeName) {
        // Called by another node after it revoked a key, and reachable directly, so it is gated too - this
        // discards a cache that the whole cluster's token verification depends on. Not audited, because it
        // is the fan-out arm of a revoke already recorded once; an entry per node would say nothing more.
        return securityContextProvider.get().secureResult(
                AppPermission.ADMINISTRATOR,
                () -> signingKeyRefreshFanOutProvider.get().refreshOnNode(nodeName));
    }

    /**
     * Make the withdrawal take effect, then record it.
     * <p>
     * The refresh is not a nicety. Every node caches the key set, so without it a revoked key would go on
     * being trusted everywhere except the node that served this request until that cache happened to
     * reload - the same shape of gap as ending a session on one node only.
     * </p>
     */
    private void afterRevoke(final int revoked, final boolean includedActiveKey, final String what) {
        if (revoked == 0) {
            // Nothing changed, so there is nothing to propagate and nothing worth auditing as an act.
            return;
        }

        signingKeyRefreshFanOutProvider.get().refreshAllNodes();

        LOGGER.info(() -> LogUtil.message(
                "Revoked {} identity signing key(s); the key that signs new tokens {} among them",
                revoked,
                includedActiveKey
                        ? "was"
                        : "was not"));

        stroomEventLoggingServiceProvider.get().log(
                "SigningKeyResourceImpl.revoke",
                "An administrator revoked " + what + " for the internal identity provider",
                AuthenticateEventAction.builder()
                        .withAction(AuthenticateAction.OTHER)
                        .withOutcome(AuthenticateOutcome.builder()
                                .withSuccess(true)
                                .withDescription(includedActiveKey
                                        ? "Everyone using the internal identity provider must sign in again"
                                        : "Anyone still holding a token signed with it must sign in again")
                                .build())
                        .withData(Data.builder()
                                .withName("KeysRevoked")
                                .withValue(String.valueOf(revoked))
                                .build())
                        // Deliberately no key identifier: the keys are not named on the screen either, and
                        // an entry naming one nobody can look up afterwards would not help.
                        .build());
    }
}
