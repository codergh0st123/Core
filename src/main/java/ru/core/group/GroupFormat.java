package ru.core.group;

final class GroupFormat {

    static final GroupFormat EMPTY = new GroupFormat("", "", "", "");

    private final String tabPrefix;
    private final String tabSuffix;
    private final String tagPrefix;
    private final String tagSuffix;

    GroupFormat(String tabPrefix, String tabSuffix, String tagPrefix, String tagSuffix) {
        this.tabPrefix = tabPrefix;
        this.tabSuffix = tabSuffix;
        this.tagPrefix = tagPrefix;
        this.tagSuffix = tagSuffix;
    }

    String tabPrefix() {
        return tabPrefix;
    }

    String tabSuffix() {
        return tabSuffix;
    }

    String tagPrefix() {
        return tagPrefix;
    }

    String tagSuffix() {
        return tagSuffix;
    }
}
