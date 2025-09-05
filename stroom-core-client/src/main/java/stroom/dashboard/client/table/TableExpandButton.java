package stroom.dashboard.client.table;

import stroom.query.api.GroupSelection;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.InlineSvgButton;

import java.util.HashSet;

public class TableExpandButton extends InlineSvgButton {

    private TableExpandButton() {
    }

    public static TableExpandButton create() {
        final TableExpandButton button = new TableExpandButton();
        button.setSvg(SvgPresets.EXPAND_ALL.getSvgImage());
        button.setTitle("Expand");
        button.setEnabled(false);
        return button;
    }


    public GroupSelection expand(final GroupSelection groupSelection, final int maxDepth) {
        return groupSelection.copy()
                .closedGroups(new HashSet<>())
                .expand(maxDepth)
                .build();
    }

    public void update(final GroupSelection groupSelection, final int maxDepth) {
        final int expandedDepth = groupSelection.getExpandedDepth();
        final boolean enableExpandButton = maxDepth > 0 &&
            (expandedDepth < maxDepth || groupSelection.hasClosedGroups());
        setEnabled(enableExpandButton);
        setTitle(enableExpandButton ? "Expand Level " + Math.min(maxDepth, expandedDepth + 1) : "Expand");
    }
}
