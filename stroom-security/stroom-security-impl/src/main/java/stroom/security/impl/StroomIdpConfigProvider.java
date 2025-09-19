package stroom.security.impl;

import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfigProvider;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class StroomIdpConfigProvider extends AbstractConfig implements IsStroomConfig, OpenIdConfigProvider {

    private static final boolean USE_TEST_CREDENTIALS_DEFAULT = false;

    //    private final boolean useIdp;  // proxy only
    private final boolean useTestCredentials;
    private final OpenIdConfiguration internal;
    private final OpenIdConfiguration external1;
    private final OpenIdConfiguration external2;
    private final OpenIdConfiguration external3;

    public StroomIdpConfigProvider() {
        // TODO init internal IDP
        this.useTestCredentials = USE_TEST_CREDENTIALS_DEFAULT;
        this.internal = null;
        this.external1 = null;
        this.external2 = null;
        this.external3 = null;

    }

    public StroomIdpConfigProvider(@JsonProperty("useTestCredentials") final Boolean useTestCredentials,
                                   @JsonProperty("internal") final OpenIdConfiguration internal,
                                   @JsonProperty("external1") final OpenIdConfiguration external1,
                                   @JsonProperty("external2") final OpenIdConfiguration external2,
                                   @JsonProperty("external3") final OpenIdConfiguration external3) {
        this.useTestCredentials = Objects.requireNonNullElse(useTestCredentials, USE_TEST_CREDENTIALS_DEFAULT);
        this.internal = internal;
        this.external1 = external1;
        this.external2 = external2;
        this.external3 = external3;
    }

    private Stream<OpenIdConfiguration> streamAllEnabled() {
        return Stream.of(internal, external1, external2)
                .filter(Objects::nonNull)
                .filter(OpenIdConfiguration::isEnabled);
    }

    @Override
    public boolean hasIDP() {
        return streamAllEnabled()
                .findAny()
                .isPresent();
    }

    @Override
    public boolean isUseTestCredentials() {
        return useTestCredentials;
    }

    @Override
    public List<OpenIdConfiguration> getAll() {
        return streamAllEnabled().toList();
    }

    @Override
    public List<OpenIdConfiguration> getByType(final IdpType idpType) {
        Objects.requireNonNull(idpType);
        return streamAllEnabled()
                .filter(openIdConfiguration ->
                        idpType == openIdConfiguration.getIdentityProviderType())
                .toList();
    }

    @Override
    public OpenIdConfiguration getByName(final String idpName) {
        Objects.requireNonNull(idpName);
        final List<OpenIdConfiguration> configs = streamAllEnabled()
                .filter(config -> idpName.equalsIgnoreCase(config.getIdpName()))
                .toList();
        if (configs.size() > 1) {
            throw new RuntimeException(LogUtil.message("Multiple IDP configurations with name '{}'", idpName));
        }
        return NullSafe.first(configs);
    }

    @Override
    public OpenIdConfiguration getInternal() {
        return internal;
    }

    @Override
    public OpenIdConfiguration getExternal1() {
        return external1;
    }

    @Override
    public OpenIdConfiguration getExternal2() {
        return external2;
    }

    @Override
    public OpenIdConfiguration getExternal3() {
        return external3;
    }
}
