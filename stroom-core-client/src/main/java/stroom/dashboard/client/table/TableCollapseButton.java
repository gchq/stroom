package stroom.dashboard.client.table;

import stroom.query.api.GroupSelection;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.InlineSvgButton;

import java.util.HashSet;

public class TableCollapseButton extends InlineSvgButton {

    private TableCollapseButton() {
    }

    public static TableCollapseButton create() {
        final TableCollapseButton button = new TableCollapseButton();
        button.setSvg(SvgPresets.COLLAPSE_ALL.getSvgImage());
        button.setTitle("Collapse");
        button.setEnabled(false);
        return button;
    }


    public GroupSelection collapse(final GroupSelection groupSelection) {
        return groupSelection.copy()
                .collapse()
                .openGroups(new HashSet<>())
                .build();
    }

    public void update(final GroupSelection groupSelection, final int maxDepth) {
        final int expandedDepth = groupSelection.getExpandedDepth();
        final boolean enableCollapseButton = maxDepth > 0 && (expandedDepth > 0 || groupSelection.hasOpenGroups());
        setEnabled(enableCollapseButton);
        setTitle(enableCollapseButton ? "Collapse Level " +
            Math.min(maxDepth, Math.max(1, groupSelection.getExpandedDepth())) : "Collapse");

    }
}
