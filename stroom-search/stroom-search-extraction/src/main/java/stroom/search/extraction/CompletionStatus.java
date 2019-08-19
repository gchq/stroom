package stroom.search.extraction;

public interface CompletionStatus {
    boolean isComplete();

    long getCount();

    void addChild(String name, CompletionStatus completionStatus);
}
