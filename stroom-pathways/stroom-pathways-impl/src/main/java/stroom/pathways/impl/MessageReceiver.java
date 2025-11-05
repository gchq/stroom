package stroom.pathways.impl;

import stroom.util.shared.Severity;

import java.util.function.Supplier;

public interface MessageReceiver {

    void log(Severity severity, Supplier<String> message);
}
