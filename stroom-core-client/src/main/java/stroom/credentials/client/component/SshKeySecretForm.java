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

import stroom.credentials.shared.Secret;
import stroom.credentials.shared.SshKeySecret;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.PasswordBox;
import stroom.widget.tickbox.client.view.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;

/// The secret of an SSH key credential: the private key, which is required, its pass phrase, and
/// the known hosts it is verified against.
public final class SshKeySecretForm extends Component implements SecretForm {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PasswordBox passPhrase;
    @UiField
    TextArea privateKey;
    @UiField
    TickBox verifyHosts;
    @UiField
    TextArea knownHosts;

    /// Makes an empty form, verifying hosts.
    @SuppressWarnings("checkstyle:linelength")
    public SshKeySecretForm() {
        initWidget(BINDER.createAndBindUi(this));
        // GWT's TextArea has no placeholder setter for its template
        privateKey.getElement().setAttribute("placeholder", """
                Example:
                -----BEGIN OPENSSH PRIVATE KEY-----
                XXX
                -----END OPENSSH PRIVATE KEY-----
                """);
        verifyHosts.setValue(true);
        knownHosts.getElement().setAttribute("placeholder", """
                Example:
                github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl
                github.com ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBEmKSENjQEezOmxkZMy7opKgwFB9nkt5YRrYMjNuG5N87uRgg6CLrbo5wAdT/y6v0mKV0U2w0WZ2YB/++Tpockg=
                github.com ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQCj7ndNxQowgcQnjshcLrqPEiiphnt+VTTvDP6mHBL9j1aNUkY4Ue1gvwnGLVlOhGeYrnZaMgRK6+PKCUXaDbC7qtbW8gIkhL7aGCsOr/C56SJMy/BCZfxd1nWzAOxSDPgVsmerOBYfNqltV9/hWCqBywINIR+5dIg6JTJ72pcEpEjcYgXkE2YEFXV1JHnsKgbLWNlhScqb2UmyRkQyytRLtL+38TGxkxCflmO+5Z8CSSNY7GidjMIZ7Q4zMjA2n1nGrlTDkzwDCsw+wqFPGQA179cnfGWOWRVruj16z6XyvxvjJwbz0wQZ75XK5tKSb7FNyeIEs4TT4jk+S4dhPeAUC5y+bDYirYgM4GC7uEnztnZyaVWQ7B381AK4Qdrwt51ZqExKbQpTUNn+EjqoTwvqNj4kqx5QUCI0ThS/YkOxJCXmPUWZbhjpCg56i+2aB6CmK2JGhn57K5mj0MNdBXA4/WnwH6XoPWJzK5Nyu2zB3nAZp+S5hpQs+p1vN1/wsjk=
                """);
    }

    @Override
    public Secret getSecret() {
        return new SshKeySecret(
                passPhrase.getValue(),
                privateKey.getValue(),
                verifyHosts.getValue(),
                knownHosts.getValue());
    }

    @Override
    public String validate() {
        if (privateKey.getValue().isBlank()) {
            return "Private key must not be empty";
        }
        return null;
    }

    @Override
    public void focus() {
        passPhrase.focus();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SshKeySecretForm> {

    }
}
