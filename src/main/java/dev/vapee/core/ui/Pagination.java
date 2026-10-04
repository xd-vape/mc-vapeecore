package dev.vapee.core.ui;

/** A clamped page and its half-open content range, independent of inventories and domain data. */
public record Pagination(int page, int pageCount, int fromIndex, int toIndex) {
    public Pagination {
        if (pageCount < 1 || page < 0 || page >= pageCount || fromIndex < 0 || toIndex < fromIndex) {
            throw new IllegalArgumentException("Invalid page window");
        }
    }

    public static Pagination of(int itemCount, int requestedPage, int contentSize) {
        if (itemCount < 0) throw new IllegalArgumentException("itemCount must not be negative");
        if (contentSize <= 0) throw new IllegalArgumentException("contentSize must be positive");
        int pageCount = itemCount == 0 ? 1 : 1 + (itemCount - 1) / contentSize;
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        // The clamped page starts below itemCount (or at zero for an empty list).
        int from = page * contentSize;
        // Add only the remaining range, avoiding from + contentSize overflow.
        int to = from + Math.min(contentSize, itemCount - from);
        return new Pagination(page, pageCount, from, to);
    }

    public boolean hasPrevious() { return page > 0; }
    public boolean hasNext() { return page < pageCount - 1; }
}
