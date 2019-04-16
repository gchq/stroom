package stroom.datafeed.server;

public interface DataReceiptPolicyMetaMapFilterFactory {
    MetaMapFilter create(String policyUuid);
}
