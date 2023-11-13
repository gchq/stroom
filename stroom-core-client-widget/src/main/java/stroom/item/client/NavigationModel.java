package stroom.item.client;

import java.util.Stack;

public class NavigationModel {

    private final Stack<SelectionItem> openItems = new Stack<>();

    public boolean navigate(SelectionItem selectionItem) {
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

    public boolean navigateBack(SelectionItem selectionItem) {
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

    public Stack<SelectionItem> getPath() {
        return openItems;
    }
}
