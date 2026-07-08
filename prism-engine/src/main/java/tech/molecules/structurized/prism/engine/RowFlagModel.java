package tech.molecules.structurized.prism.engine;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class RowFlagModel {
    private final Map<String, BitSet> flags = new LinkedHashMap<>();

    public void setFlagged(String flagName, int physicalRow, boolean flagged) {
        requireFlagName(flagName);
        flags.computeIfAbsent(flagName, ignored -> new BitSet()).set(physicalRow, flagged);
    }

    public boolean isFlagged(String flagName, int physicalRow) {
        BitSet rows = flags.get(flagName);
        return rows != null && rows.get(physicalRow);
    }

    public BitSet rowsForFlag(String flagName) {
        BitSet rows = flags.get(flagName);
        return rows == null ? new BitSet() : (BitSet) rows.clone();
    }

    public Set<String> flagNames() {
        return Set.copyOf(flags.keySet());
    }

    private static void requireFlagName(String flagName) {
        if (flagName == null || flagName.isBlank()) {
            throw new IllegalArgumentException("flagName must not be blank");
        }
    }
}
