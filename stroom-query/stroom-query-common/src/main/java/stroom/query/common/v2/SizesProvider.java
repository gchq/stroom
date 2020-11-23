package stroom.query.common.v2;

public interface SizesProvider {
    Sizes getDefaultMaxResultsSizes();

    Sizes getStoreSizes();
}
