package unievents.domain;

/** Event category (e.g. Tech, Cultural, Sports). */
public class Category {

    private final int categoryId;
    private final String name;

    public Category(int categoryId, String name) {
        this.categoryId = categoryId;
        this.name = name;
    }

    public int getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
