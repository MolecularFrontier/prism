package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PrismGrouping {
    private final String id;
    private final String title;
    private final String description;
    private final String sourceRowSetId;
    private final PrismGroupingMode mode;
    private final List<PrismGroup> groups;
    private final List<PrismGroupMembership> memberships;
    private final String facetColumnId;
    private final Map<String, Object> provenance;
    private final Map<String, PrismGroup> groupsById;
    private final Map<String, List<PrismGroupMembership>> membershipsByRowId;
    private final Map<String, Set<String>> rowIdsByGroupId;

    public PrismGrouping(String id,
                         String title,
                         String description,
                         String sourceRowSetId,
                         PrismGroupingMode mode,
                         List<PrismGroup> groups,
                         List<PrismGroupMembership> memberships,
                         String facetColumnId,
                         Map<String, Object> provenance) {
        this.id = requireText(id, "grouping id");
        this.title = title == null || title.isBlank() ? this.id : title.trim();
        this.description = description == null ? "" : description.trim();
        this.sourceRowSetId = optionalText(sourceRowSetId);
        this.mode = mode == null ? PrismGroupingMode.EXCLUSIVE : mode;
        this.groups = groups == null ? List.of() : List.copyOf(groups);
        this.memberships = memberships == null ? List.of() : List.copyOf(memberships);
        this.facetColumnId = optionalText(facetColumnId);
        this.provenance = provenance == null || provenance.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(provenance));
        if (this.mode == PrismGroupingMode.EXCLUSIVE && this.facetColumnId == null) {
            throw new IllegalArgumentException("exclusive grouping facet column id must not be blank");
        }
        if (this.mode == PrismGroupingMode.OVERLAPPING && this.facetColumnId != null) {
            throw new IllegalArgumentException("overlapping groupings cannot define a scalar facet column");
        }
        this.groupsById = indexGroups(this.groups);
        validateHierarchy(this.groupsById);
        MembershipIndex membershipIndex = indexMemberships(this.memberships, this.groupsById, this.mode);
        this.membershipsByRowId = membershipIndex.membershipsByRowId();
        this.rowIdsByGroupId = membershipIndex.rowIdsByGroupId();
        validateRepresentatives(this.groups, this.rowIdsByGroupId);
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String sourceRowSetId() {
        return sourceRowSetId;
    }

    public PrismGroupingMode mode() {
        return mode;
    }

    public List<PrismGroup> groups() {
        return groups;
    }

    public List<PrismGroupMembership> memberships() {
        return memberships;
    }

    public String facetColumnId() {
        return facetColumnId;
    }

    public Map<String, Object> provenance() {
        return provenance;
    }

    public PrismGroup group(String groupId) {
        PrismGroup group = groupsById.get(groupId);
        if (group == null) {
            throw new IllegalArgumentException("unknown group '" + groupId + "' in grouping '" + id + "'");
        }
        return group;
    }

    public List<PrismGroupMembership> membershipsForRow(String rowId) {
        return membershipsByRowId.getOrDefault(rowId, List.of());
    }

    public Optional<PrismGroupMembership> exclusiveMembership(String rowId) {
        if (mode != PrismGroupingMode.EXCLUSIVE) {
            throw new IllegalStateException("grouping '" + id + "' is not exclusive");
        }
        List<PrismGroupMembership> rowMemberships = membershipsForRow(rowId);
        return rowMemberships.isEmpty() ? Optional.empty() : Optional.of(rowMemberships.getFirst());
    }

    public Set<String> rowsInGroup(String groupId) {
        group(groupId);
        return rowIdsByGroupId.getOrDefault(groupId, Set.of());
    }

    public PrismColumnSchema facetSchema() {
        if (facetColumnId == null) {
            throw new IllegalStateException("grouping '" + id + "' has no scalar facet");
        }
        LinkedHashMap<String, Object> raw = new LinkedHashMap<>();
        raw.put("groupingId", id);
        if (sourceRowSetId != null) {
            raw.put("sourceRowSetId", sourceRowSetId);
        }
        return new PrismColumnSchema(
                facetColumnId,
                PrismColumnType.CATEGORICAL,
                title,
                "group_membership",
                "grouping_facet",
                null,
                null,
                null,
                null,
                raw
        );
    }

    private static Map<String, PrismGroup> indexGroups(List<PrismGroup> groups) {
        LinkedHashMap<String, PrismGroup> indexed = new LinkedHashMap<>();
        for (PrismGroup group : groups) {
            if (indexed.putIfAbsent(group.id(), group) != null) {
                throw new IllegalArgumentException("duplicate group id '" + group.id() + "'");
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    private static void validateHierarchy(Map<String, PrismGroup> groups) {
        for (PrismGroup group : groups.values()) {
            String parentId = group.parentGroupId();
            if (parentId != null && !groups.containsKey(parentId)) {
                throw new IllegalArgumentException("group '" + group.id() + "' references unknown parent group '" + parentId + "'");
            }
            if (group.id().equals(parentId)) {
                throw new IllegalArgumentException("group '" + group.id() + "' cannot be its own parent");
            }
        }
        HashSet<String> complete = new HashSet<>();
        for (String groupId : groups.keySet()) {
            detectCycle(groupId, groups, new LinkedHashSet<>(), complete);
        }
    }

    private static void detectCycle(String groupId,
                                    Map<String, PrismGroup> groups,
                                    LinkedHashSet<String> path,
                                    Set<String> complete) {
        if (complete.contains(groupId)) {
            return;
        }
        if (!path.add(groupId)) {
            throw new IllegalArgumentException("group hierarchy contains a cycle at '" + groupId + "'");
        }
        String parentId = groups.get(groupId).parentGroupId();
        if (parentId != null) {
            detectCycle(parentId, groups, path, complete);
        }
        path.remove(groupId);
        complete.add(groupId);
    }

    private static MembershipIndex indexMemberships(List<PrismGroupMembership> memberships,
                                                    Map<String, PrismGroup> groups,
                                                    PrismGroupingMode mode) {
        LinkedHashMap<String, ArrayList<PrismGroupMembership>> byRow = new LinkedHashMap<>();
        LinkedHashMap<String, LinkedHashSet<String>> byGroup = new LinkedHashMap<>();
        HashSet<MembershipKey> pairs = new HashSet<>();
        for (PrismGroupMembership membership : memberships) {
            if (!groups.containsKey(membership.groupId())) {
                throw new IllegalArgumentException("membership references unknown group '" + membership.groupId() + "'");
            }
            if (!pairs.add(new MembershipKey(membership.rowId(), membership.groupId()))) {
                throw new IllegalArgumentException("duplicate membership for row '" + membership.rowId()
                        + "' and group '" + membership.groupId() + "'");
            }
            ArrayList<PrismGroupMembership> rowMemberships =
                    byRow.computeIfAbsent(membership.rowId(), ignored -> new ArrayList<>());
            if (mode == PrismGroupingMode.EXCLUSIVE && !rowMemberships.isEmpty()) {
                throw new IllegalArgumentException("exclusive grouping assigns row '" + membership.rowId()
                        + "' to more than one group");
            }
            rowMemberships.add(membership);
            byGroup.computeIfAbsent(membership.groupId(), ignored -> new LinkedHashSet<>()).add(membership.rowId());
        }
        LinkedHashMap<String, List<PrismGroupMembership>> immutableByRow = new LinkedHashMap<>();
        byRow.forEach((rowId, values) -> immutableByRow.put(rowId, List.copyOf(values)));
        LinkedHashMap<String, Set<String>> immutableByGroup = new LinkedHashMap<>();
        byGroup.forEach((groupId, values) -> immutableByGroup.put(groupId,
                Collections.unmodifiableSet(new LinkedHashSet<>(values))));
        return new MembershipIndex(
                Collections.unmodifiableMap(immutableByRow),
                Collections.unmodifiableMap(immutableByGroup)
        );
    }

    private static void validateRepresentatives(List<PrismGroup> groups, Map<String, Set<String>> rowsByGroup) {
        for (PrismGroup group : groups) {
            if (group.representativeRowId() != null
                    && !rowsByGroup.getOrDefault(group.id(), Set.of()).contains(group.representativeRowId())) {
                throw new IllegalArgumentException("representative row '" + group.representativeRowId()
                        + "' is not a member of group '" + group.id() + "'");
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record MembershipIndex(
            Map<String, List<PrismGroupMembership>> membershipsByRowId,
            Map<String, Set<String>> rowIdsByGroupId
    ) {
    }

    private record MembershipKey(String rowId, String groupId) {
    }
}
