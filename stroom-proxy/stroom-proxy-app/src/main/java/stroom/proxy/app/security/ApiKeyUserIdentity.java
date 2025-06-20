package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;
import stroom.util.shared.UserDesc;

import java.util.Objects;
import java.util.Optional;

public class ApiKeyUserIdentity implements UserIdentity {

    private final String apiKey;
    private final UserDesc userDesc;

    public ApiKeyUserIdentity(final String apiKey, final UserDesc userDesc) {
        this.apiKey = Objects.requireNonNull(apiKey);
        this.userDesc = Objects.requireNonNull(userDesc);
    }

    @Override
    public String subjectId() {
        return userDesc.getSubjectId();
    }

    @Override
    public String getDisplayName() {
        return userDesc.getDisplayName();
    }

    @Override
    public Optional<String> getFullName() {
        return Optional.ofNullable(userDesc.getFullName());
    }

    public UserDesc getUserDesc() {
        return userDesc;
    }

    public String getApiKey() {
        return apiKey;
    }

    @Override
    public String toString() {
        return "ApiKeyUserIdentity{" +
               "apiKey='" + apiKey + '\'' +
               ", userDesc=" + userDesc +
               '}';
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final ApiKeyUserIdentity that = (ApiKeyUserIdentity) object;
        return Objects.equals(apiKey, that.apiKey)
               && Objects.equals(userDesc, that.userDesc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiKey, userDesc);
    }
}
