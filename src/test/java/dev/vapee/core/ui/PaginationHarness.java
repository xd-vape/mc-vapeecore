package dev.vapee.core.ui;

public final class PaginationHarness {
    private static int checks;

    public static void main(String[] args) {
        for (int count : new int[]{0, 1, 44, 45, 46, 89, 90, 91, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
            for (int size : new int[]{1, 2, 45, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
                for (int requested : new int[]{Integer.MIN_VALUE, -1, 0, 1, 2, 100, Integer.MAX_VALUE}) {
                    Pagination page = Pagination.of(count, requested, size);
                    long expectedCount = Math.max(1L, ((long) count + size - 1) / size);
                    long expectedPage = Math.max(0L, Math.min((long) requested, expectedCount - 1));
                    long from = expectedPage * size;
                    long to = Math.min(from + size, count);
                    check(page.pageCount() == expectedCount && page.page() == expectedPage, "clamped page/count");
                    check(page.fromIndex() == from && page.toIndex() == to, "exact long-oracle slice");
                    check(0 <= page.fromIndex() && page.fromIndex() <= page.toIndex() && page.toIndex() <= count,
                            "range stays inside input");
                    check(page.hasPrevious() == (expectedPage > 0) && page.hasNext() == (expectedPage + 1 < expectedCount),
                            "bounded navigation");
                }
            }
        }
        check(Pagination.of(0, 99, 45).equals(new Pagination(0, 1, 0, 0)), "empty single page");
        rejects(() -> Pagination.of(-1, 0, 45));
        rejects(() -> Pagination.of(1, 0, 0));
        rejects(() -> Pagination.of(1, 0, -1));
        rejects(() -> new Pagination(-1, 1, 0, 0));
        rejects(() -> new Pagination(0, 0, 0, 0));
        rejects(() -> new Pagination(1, 1, 0, 0));
        rejects(() -> new Pagination(0, 1, -1, 0));
        rejects(() -> new Pagination(0, 1, 1, 0));
        System.out.println("PaginationHarness passed " + checks + " checks.");
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("invalid pagination accepted");
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
