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

package stroom.feed.client.component;

import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedDoc.FeedStatus;
import stroom.item.client.SelectionBox;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// A feed's Settings tab: its classification, whether it holds reference data, what happens to
/// data sent to it, the type, encodings, formats and schema of what it receives, and where it is
/// stored. Its controller ([FeedSettingsController]) gives it the encodings, types and volume
/// groups to choose from.
public class FeedSettings extends Component {

    // An encoding left blank is UTF-8
    private static final String DEFAULT_ENCODING = "UTF-8";

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox classification;
    @UiField
    SelectionBox<String> dataEncoding;
    @UiField
    SelectionBox<String> contextEncoding;
    @UiField
    SelectionBox<FeedStatus> feedStatus;
    @UiField
    SelectionBox<String> receivedType;
    @UiField
    SelectionBox<String> dataFormat;
    @UiField
    SelectionBox<String> contextFormat;
    @UiField
    TextBox schema;
    @UiField
    TextBox schemaVersion;
    @UiField
    TickBox reference;
    @UiField
    SelectionBox<String> volumeGroup;

    private Runnable onChange = () -> {
    };
    // The feed shown, so the lists can show its values once they come
    private FeedDoc feed;

    /// Makes an empty tab.
    public FeedSettings() {
        initWidget(BINDER.createAndBindUi(this));
        feedStatus.addItems(FeedStatus.values());

        classification.addValueChangeHandler(e -> onChange.run());
        reference.addValueChangeHandler(e -> onChange.run());
        dataEncoding.addValueChangeHandler(event -> {
            dataEncoding.setValue(ensureEncoding(dataEncoding.getValue()));
            onChange.run();
        });
        contextEncoding.addValueChangeHandler(event -> {
            contextEncoding.setValue(ensureEncoding(contextEncoding.getValue()));
            onChange.run();
        });
        feedStatus.addValueChangeHandler(event -> onChange.run());
        dataFormat.addValueChangeHandler(event -> onChange.run());
        contextFormat.addValueChangeHandler(event -> onChange.run());
        schema.addValueChangeHandler(event -> onChange.run());
        schemaVersion.addValueChangeHandler(event -> onChange.run());
        receivedType.addValueChangeHandler(event -> onChange.run());
        volumeGroup.addValueChangeHandler(event -> onChange.run());
    }

    /// @param formats The formats the data and context can be in, e.g. XML.
    public void setFormats(final List<String> formats) {
        dataFormat.addItems(formats);
        contextFormat.addItems(formats);
    }

    /// @param encodings The character encodings the data and context can be in.
    public void setEncodings(final List<String> encodings) {
        dataEncoding.clear();
        contextEncoding.clear();
        if (NullSafe.hasItems(encodings)) {
            for (final String encoding : encodings) {
                dataEncoding.addItem(encoding);
                contextEncoding.addItem(encoding);
            }
        }
        if (feed != null) {
            dataEncoding.setValue(ensureEncoding(feed.getEncoding()));
            contextEncoding.setValue(ensureEncoding(feed.getContextEncoding()));
        }
    }

    /// @param volumeGroups The names of the volume groups the data can be stored in.
    public void setVolumeGroups(final List<String> volumeGroups) {
        volumeGroup.clear();
        volumeGroup.setNonSelectString("");
        volumeGroup.addItems(volumeGroups);
        if (feed != null) {
            volumeGroup.setValue(feed.getVolumeGroup());
        }
    }

    /// @param types The stream types received data can be given.
    public void setReceivedTypes(final List<String> types) {
        receivedType.clear();
        if (NullSafe.hasItems(types)) {
            receivedType.addItems(types);
            if (feed != null) {
                receivedType.setValue(feed.getStreamType());
            }
        }
    }

    /// @param enabled Whether the feed's status can be chosen, which it can only when receipt is
    ///                checked against it.
    public void setFeedStatusEnabled(final boolean enabled) {
        feedStatus.setEnabled(enabled);
    }

    /// Shows a feed's settings.
    ///
    /// @param feed The feed.
    public void read(final FeedDoc feed) {
        this.feed = feed;
        reference.setValue(feed.isReference());
        classification.setText(feed.getClassification());
        dataEncoding.setValue(ensureEncoding(feed.getEncoding()));
        contextEncoding.setValue(ensureEncoding(feed.getContextEncoding()));
        receivedType.setValue(feed.getStreamType());
        dataFormat.setValue(feed.getDataFormat());
        contextFormat.setValue(feed.getContextFormat());
        schema.setValue(feed.getSchema());
        schemaVersion.setValue(feed.getSchemaVersion());
        feedStatus.setValue(feed.getStatus());
        volumeGroup.setValue(feed.getVolumeGroup());
    }

    /// Writes the settings shown into a copy of a feed.
    ///
    /// @param feed The feed.
    /// @return A copy of it with the settings shown.
    public FeedDoc write(final FeedDoc feed) {
        return feed
                .copy()
                .reference(reference.getValue())
                .classification(classification.getText())
                .encoding(ensureEncoding(dataEncoding.getValue()))
                .contextEncoding(ensureEncoding(contextEncoding.getValue()))
                .streamType(receivedType.getValue())
                .dataFormat(dataFormat.getValue())
                .contextFormat(contextFormat.getValue())
                .schema(schema.getValue())
                .schemaVersion(schemaVersion.getValue())
                .status(feedStatus.getValue())
                .volumeGroup(volumeGroup.getValue())
                .build();
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        classification.setReadOnly(readOnly);
        dataEncoding.setReadOnly(readOnly);
        contextEncoding.setReadOnly(readOnly);
        receivedType.setReadOnly(readOnly);
        feedStatus.setReadOnly(readOnly);
        reference.setReadOnly(readOnly);
        volumeGroup.setReadOnly(readOnly);
        dataFormat.setReadOnly(readOnly);
        contextFormat.setReadOnly(readOnly);
        schema.setReadOnly(readOnly);
        schemaVersion.setReadOnly(readOnly);
    }

    private static String ensureEncoding(final String encoding) {
        if (NullSafe.isBlankString(encoding)) {
            return DEFAULT_ENCODING;
        }
        return encoding;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FeedSettings> {

    }
}
