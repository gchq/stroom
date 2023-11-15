package stroom.item.client;

import java.util.Stack;

public class NavigationModel<I extends SelectionItem> {

    private final Stack<I> openItems = new Stack<>();

    public boolean navigate(I selectionItem) {
        openItems.push(selectionItem);
        return true;
    }

    public boolean navigateBack() {
        if (!openItems.empty()) {
            openItems.pop();
            return true;
        }
        return false;
    }

    public boolean navigateBack(I selectionItem) {
        if (!openItems.empty()) {
            if (selectionItem == null) {
                openItems.clear();
                return true;
            } else {
                while (!openItems.empty() && !openItems.peek().equals(selectionItem)) {
                    openItems.pop();
                }
                return true;
            }
        }
        return false;
    }

    public Stack<I> getPath() {
        return openItems;
    }
}
