package stroom.item.client;

import stroom.svg.shared.SvgImage;

public interface SelectionItem {

    String getLabel();

    SvgImage getIcon();

    boolean isHasChildren();
}
