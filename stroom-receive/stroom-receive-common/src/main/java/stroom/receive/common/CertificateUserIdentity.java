package stroom.receive.common;

import stroom.security.api.UserIdentity;

import java.util.Objects;

public class CertificateUserIdentity implements UserIdentity {

    private final String commonName;

    public CertificateUserIdentity(final String commonName) {
        this.commonName = Objects.requireNonNull(commonName);
    }

    @Override
    public String getId() {
        return commonName;
    }

    @Override
    public String toString() {
        return "CertificateUserIdentity{" +
                "commonName='" + commonName + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CertificateUserIdentity that = (CertificateUserIdentity) o;
        return Objects.equals(commonName, that.commonName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(commonName);
    }
}
