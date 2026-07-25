package tech.molecules.structurized.prism.engine;

import java.util.Set;

final class GroupingFacetPrismColumn implements PrismColumn {
    private final PrismGrouping grouping;
    private final RowIdIndex rowIdIndex;
    private final PrismColumnSchema schema;

    GroupingFacetPrismColumn(PrismGrouping grouping, RowIdIndex rowIdIndex) {
        this.grouping = grouping;
        this.rowIdIndex = rowIdIndex;
        this.schema = grouping.facetSchema();
    }

    @Override
    public String id() {
        return schema.id();
    }

    @Override
    public PrismColumnType type() {
        return PrismColumnType.CATEGORICAL;
    }

    @Override
    public PrismColumnSchema schema() {
        return schema;
    }

    @Override
    public int rowCount() {
        return rowIdIndex.rowCount();
    }

    @Override
    public boolean isMissing(int physicalRow) {
        return grouping.exclusiveMembership(rowIdIndex.rowId(physicalRow)).isEmpty();
    }

    @Override
    public Object valueAt(int physicalRow) {
        return grouping.exclusiveMembership(rowIdIndex.rowId(physicalRow))
                .map(PrismGroupMembership::groupId)
                .orElse(null);
    }

    @Override
    public String formattedValueAt(int physicalRow) {
        Object value = valueAt(physicalRow);
        return value == null ? "" : grouping.group((String) value).label();
    }

    @Override
    public Set<FilterCapability> filterCapabilities() {
        return Set.of(
                FilterCapability.CATEGORY_INCLUDE,
                FilterCapability.TEXT_CONTAINS,
                FilterCapability.MISSING_VALUE
        );
    }
}
