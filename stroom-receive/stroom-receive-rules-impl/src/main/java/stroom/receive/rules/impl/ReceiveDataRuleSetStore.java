package stroom.receive.rules.impl;

import stroom.docstore.api.DocumentStore;
import stroom.receive.rules.shared.ReceiveDataRules;

public interface ReceiveDataRuleSetStore extends DocumentStore<ReceiveDataRules> {

    ReceiveDataRules getOrCreate();
}
