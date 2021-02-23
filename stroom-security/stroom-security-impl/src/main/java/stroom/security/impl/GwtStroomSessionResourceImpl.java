package stroom.security.impl;

import stroom.security.shared.GwtStroomSessionResource;

import javax.inject.Inject;

/**
 * This class exists as a bridge to an interface that the GWT code can see.
 */
public class GwtStroomSessionResourceImpl implements GwtStroomSessionResource {
    private StroomSessionResourceImpl stroomSessionResource;

    @Inject
    public GwtStroomSessionResourceImpl(final StroomSessionResourceImpl stroomSessionResource) {
        this.stroomSessionResource = stroomSessionResource;
    }

    @Override
    public Boolean gwtInvalidateStroomSession() {
        return stroomSessionResource.invalidateStroomSession();
    }
}
