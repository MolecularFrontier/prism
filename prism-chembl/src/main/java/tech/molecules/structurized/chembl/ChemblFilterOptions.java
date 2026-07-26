package tech.molecules.structurized.chembl;

import java.util.LinkedHashSet;
import java.util.Set;

public record ChemblFilterOptions(
        int minHeavyAtoms,
        int maxHeavyAtoms,
        int minCharge,
        int maxCharge,
        Set<Integer> allowedElements
) {
    public ChemblFilterOptions {
        if (minHeavyAtoms < 0 || maxHeavyAtoms < minHeavyAtoms) throw new IllegalArgumentException("invalid heavy-atom bounds");
        if (minCharge > maxCharge) throw new IllegalArgumentException("invalid charge bounds");
        allowedElements = Set.copyOf(allowedElements);
    }

    public static ChemblFilterOptions defaults() {
        return new ChemblFilterOptions(8, 60, -2, 2, new LinkedHashSet<>(Set.of(5, 6, 7, 8, 9, 11, 12, 14, 15, 16, 17, 19, 20, 34, 35, 53)));
    }
}
