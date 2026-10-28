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

package stroom.http.client.component;

import stroom.credentials.shared.Credential;
import stroom.item.client.SelectionBox;
import stroom.item.client.SelectionListModel;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/// An HTTP client's TLS settings: the protocol, the key and trust stores and what may be used.
/// Blank text is read back as `null`, and lists are one item per line.
public class HttpTlsConfigEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox protocol;
    @UiField
    TextBox provider;
    @UiField
    SelectionBox<Credential> keyStore;
    @UiField
    SelectionBox<Credential> trustStore;
    @UiField
    TickBox trustSelfSignedCertificates;
    @UiField
    TickBox verifyHostname;
    @UiField
    TextArea supportedProtocols;
    @UiField
    TextArea supportedCiphers;
    @UiField
    TextBox certAlias;

    /// Makes an empty form.
    public HttpTlsConfigEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        protocol.setReadOnly(readOnly);
        provider.setReadOnly(readOnly);
        keyStore.setReadOnly(readOnly);
        trustStore.setReadOnly(readOnly);
        trustSelfSignedCertificates.setReadOnly(readOnly);
        verifyHostname.setReadOnly(readOnly);
        supportedProtocols.setReadOnly(readOnly);
        supportedCiphers.setReadOnly(readOnly);
        certAlias.setReadOnly(readOnly);
    }

    /// Puts the focus on the protocol.
    public void focus() {
        protocol.setFocus(true);
    }

    /// @param protocol The TLS protocol, e.g. TLSv1.3.
    public void setProtocol(final String protocol) {
        this.protocol.setValue(protocol);
    }

    /// @return The TLS protocol, or `null` if blank.
    public String getProtocol() {
        return getText(protocol.getValue());
    }

    /// @param provider The Java security provider.
    public void setProvider(final String provider) {
        this.provider.setValue(provider);
    }

    /// @return The Java security provider, or `null` if blank.
    public String getProvider() {
        return getText(provider.getValue());
    }

    /// @param model The key stores to choose from.
    public void setKeyStoreModel(final SelectionListModel<Credential, SimpleSelectionItemWrapper<Credential>> model) {
        keyStore.setModel(model);
    }

    /// @param credential The key store chosen, or `null` for none.
    public void setKeyStore(final Credential credential) {
        keyStore.setValue(credential);
    }

    /// @return The key store's name, or `null` if none is chosen.
    public String getKeyStoreName() {
        return NullSafe.get(keyStore.getValue(), Credential::getName);
    }

    /// @param model The trust stores to choose from.
    public void setTrustStoreModel(final SelectionListModel<Credential, SimpleSelectionItemWrapper<Credential>> model) {
        trustStore.setModel(model);
    }

    /// @param credential The trust store chosen, or `null` for none.
    public void setTrustStore(final Credential credential) {
        trustStore.setValue(credential);
    }

    /// @return The trust store's name, or `null` if none is chosen.
    public String getTrustStoreName() {
        return NullSafe.get(trustStore.getValue(), Credential::getName);
    }

    /// @param trustSelfSignedCertificates Whether a self-signed server certificate is trusted.
    public void setTrustSelfSignedCertificates(final boolean trustSelfSignedCertificates) {
        this.trustSelfSignedCertificates.setValue(trustSelfSignedCertificates);
    }

    /// @return Whether a self-signed server certificate is trusted.
    public boolean isTrustSelfSignedCertificates() {
        return trustSelfSignedCertificates.getValue();
    }

    /// @param verifyHostname Whether the server certificate's host name is checked.
    public void setVerifyHostname(final boolean verifyHostname) {
        this.verifyHostname.setValue(verifyHostname);
    }

    /// @return Whether the server certificate's host name is checked.
    public boolean isVerifyHostname() {
        return verifyHostname.getValue();
    }

    /// @param supportedProtocols The protocol versions that may be used, or `null` for the
    ///                           defaults.
    public void setSupportedProtocols(final List<String> supportedProtocols) {
        this.supportedProtocols.setValue(listToText(supportedProtocols));
    }

    /// @return The protocol versions that may be used, or `null` if none are given.
    public List<String> getSupportedProtocols() {
        return textToList(supportedProtocols.getValue());
    }

    /// @param supportedCiphers The cipher suites that may be used, or `null` for the defaults.
    public void setSupportedCiphers(final List<String> supportedCiphers) {
        this.supportedCiphers.setValue(listToText(supportedCiphers));
    }

    /// @return The cipher suites that may be used, or `null` if none are given.
    public List<String> getSupportedCiphers() {
        return textToList(supportedCiphers.getValue());
    }

    /// @param certAlias The client certificate's alias.
    public void setCertAlias(final String certAlias) {
        this.certAlias.setValue(certAlias);
    }

    /// @return The client certificate's alias, or `null` if blank.
    public String getCertAlias() {
        return getText(certAlias.getValue());
    }

    private static String listToText(final List<String> list) {
        if (NullSafe.isEmptyCollection(list)) {
            return "";
        }
        return list.stream().collect(Collectors.joining("\n"));
    }

    private static List<String> textToList(final String text) {
        if (!NullSafe.isBlankString(text)) {
            final String[] parts = text.split("\n");
            final List<String> list = new ArrayList<>(parts.length);
            for (final String part : parts) {
                if (!NullSafe.isBlankString(part)) {
                    list.add(part);
                }
            }
            if (!NullSafe.isEmptyCollection(list)) {
                return list;
            }
        }
        return null;
    }

    private static String getText(final String value) {
        if (NullSafe.isBlankString(value)) {
            return null;
        }
        return value.trim();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, HttpTlsConfigEditor> {

    }
}
