package dev.whizzle.itembeacon.client;

/**
 * Where the search bar and the two rows of sort buttons go on a container screen, from {@link ToolSettings} and the
 * screen's geometry (all in screen pixels). Pure Java, so the fallbacks are tested:
 * <ul>
 * <li>RIGHT that doesn't fit the window goes LEFT (and LEFT that doesn't, RIGHT).</li>
 * <li>TOP or a search bar above the panel with no room above goes RIGHT.</li>
 * <li>TITLE where the title text would be covered goes TOP (container) or BOTTOM (inventory).</li>
 * <li>BOTTOM with no room below goes RIGHT.</li>
 * </ul>
 */
public final class ToolLayout {
    private ToolLayout() {}

    public static final int COLUMN = 92, ROW = 14, SMALL = 11, GAP = 4, BUTTON = 30, PAD = 7;

    public record Rect(int x, int y, int w, int h) {}

    /**
     * {@code containerTop} / {@code playerTop}: the y of the first slot row (panel-relative), -1 if none.
     * {@code titleEnd} / {@code inventoryLabelEnd}: where those texts end (panel-relative x), -1 if not drawn.
     * {@code titleY} / {@code inventoryLabelY}: their y (panel-relative).
     */
    public record Input(int screenW, int screenH, int left, int top, int imageW, int imageH,
                        boolean containerRow, int containerTop, int titleEnd, int titleY,
                        boolean playerRow, int playerTop, int inventoryLabelEnd, int inventoryLabelY) {}

    /** {@code containerRow} / {@code playerRow} are null when there are no such buttons. */
    public record Layout(Rect search, Rect containerRow, Rect playerRow) {}

    public static Layout compute(Input in, ToolSettings s) {
        int right = in.left() + in.imageW() + GAP, leftX = in.left() - GAP - COLUMN;
        boolean rightFits = right + COLUMN <= in.screenW() - 2, leftFits = leftX >= 2;
        int sideX = rightFits || !leftFits ? right : leftX;
        int otherSideX = leftFits || !rightFits ? leftX : right;
        int above = in.top() - ROW - 2;
        boolean roomAbove = above >= 2;

        // ---- search + container buttons ----
        ToolSettings.Container c = s.container;
        int rowX = in.left() + in.imageW() - PAD - COLUMN;
        if (c == ToolSettings.Container.TITLE && in.containerRow()
            && (in.titleEnd() >= 0 && in.left() + in.titleEnd() + GAP > rowX)) c = ToolSettings.Container.TOP;
        if ((c == ToolSettings.Container.TOP || c == ToolSettings.Container.TITLE) && !roomAbove) c = ToolSettings.Container.RIGHT;
        Rect search, crow = null;
        switch (c) {
            case TOP -> {
                if (in.containerRow()) {
                    search = new Rect(in.left(), above, in.imageW() - COLUMN - GAP, ROW);
                    crow = new Rect(in.left() + in.imageW() - COLUMN, above, COLUMN, ROW);
                } else search = new Rect(in.left() + in.imageW() - COLUMN, above, COLUMN, ROW);
            }
            case TITLE -> {
                search = new Rect(in.left() + in.imageW() - COLUMN, above, COLUMN, ROW);
                if (in.containerRow()) crow = new Rect(rowX, in.top() + in.titleY() - 2, COLUMN, SMALL);
            }
            default -> {
                int x = c == ToolSettings.Container.LEFT ? otherSideX : sideX;
                search = new Rect(x, in.top(), COLUMN, ROW);
                if (in.containerRow()) crow = new Rect(x, in.top() + Math.max(in.containerTop() - 1, ROW + 2), COLUMN, ROW);
            }
        }

        // ---- inventory buttons ----
        Rect prow = null;
        if (in.playerRow()) {
            ToolSettings.Inventory p = s.inventory;
            if (p == ToolSettings.Inventory.TITLE && in.inventoryLabelEnd() >= 0 && in.left() + in.inventoryLabelEnd() + GAP > rowX)
                p = ToolSettings.Inventory.BOTTOM;
            int below = in.top() + in.imageH() + 2;
            if (p == ToolSettings.Inventory.BOTTOM && below + ROW > in.screenH() - 2) p = ToolSettings.Inventory.RIGHT;
            prow = switch (p) {
                case BOTTOM -> new Rect(in.left() + in.imageW() - COLUMN, below, COLUMN, ROW);
                case TITLE -> new Rect(rowX, in.top() + in.inventoryLabelY() - 2, COLUMN, SMALL);
                default -> {
                    int x = p == ToolSettings.Inventory.LEFT ? otherSideX : sideX;
                    int y = in.top() + in.playerTop() - 1;
                    // Don't sit on the search bar or the container row on the same side.
                    for (Rect r : new Rect[] {search, crow})
                        if (r != null && r.x() == x && y < r.y() + r.h() + 2 && y + ROW > r.y()) y = r.y() + r.h() + 2;
                    yield new Rect(x, y, COLUMN, ROW);
                }
            };
        }
        return new Layout(search, crow, prow);
    }
}
