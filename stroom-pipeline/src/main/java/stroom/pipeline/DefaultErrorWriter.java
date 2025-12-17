/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline;

import stroom.pipeline.destination.Destination;
import stroom.pipeline.destination.DestinationProvider;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.ElementId;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

@PipelineScoped
public class DefaultErrorWriter implements ErrorWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultErrorWriter.class);

    private static final String NEW_LINE = "\n";
    private static final String OPEN_BRACKET = "[";
    private static final String CLOSE_BRACKET = "] ";
    private static final String SPACE = " ";
    private static final String COLON = ":";

    private List<DestinationProvider> destinationProviders;

    public void addOutputStreamProvider(final DestinationProvider outputStreamProvider) {
        if (destinationProviders == null) {
            destinationProviders = new ArrayList<>();
        }
        destinationProviders.add(outputStreamProvider);
    }

    @Override
    public void log(final Severity severity, final Location location, final ElementId elementId, final String message) {
        if (message != null && destinationProviders != null && !destinationProviders.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append(OPEN_BRACKET);
            if (location != null) {
                sb.append(location);
                sb.append(SPACE);
            }
            sb.append(elementId);
            sb.append(CLOSE_BRACKET);
            sb.append(severity.getDisplayValue());
            sb.append(COLON);
            sb.append(SPACE);

            // We have a message so print it without new lines.
            final char[] chars = message.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                final char c = chars[i];
                switch (c) {
                    case '\n':
                        sb.append(' ');
                        break;
                    case '\r':
                        break;
                    default:
                        sb.append(c);
                        break;
                }
            }

            // Print a new line.
            sb.append(NEW_LINE);

            for (final DestinationProvider destinationProvider : destinationProviders) {
                try {
                    final Destination destination = destinationProvider.borrowDestination();
                    if (destination != null) {
                        try {
                            final OutputStream outputStream = destination.getOutputStream();
                            if (outputStream != null) {
                                outputStream.write(sb.toString().getBytes());
                            }
                        } catch (final IOException e) {
                            LOGGER.error(MarkerFactory.getMarker("FATAL"), "Unable to write to error stream", e);
                        }

                        destinationProvider.returnDestination(destination);
                    }
                } catch (final IOException e) {
                    LOGGER.error(MarkerFactory.getMarker("FATAL"), "Unable to write to error stream", e);
                }
            }
        }
    }
}
