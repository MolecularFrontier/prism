package tech.molecules.structurized.chembl;

public record ChemblExportOptions(
        long maxAccepted,
        long maxScanned,
        Selection selection,
        long seed,
        ChemblFilterOptions filter,
        boolean includeSourceSmiles
) {
    public ChemblExportOptions {
        if (maxAccepted < 1 || maxScanned < 1) throw new IllegalArgumentException("limits must be positive");
        selection = selection == null ? Selection.SEQUENTIAL : selection;
        filter = filter == null ? ChemblFilterOptions.defaults() : filter;
    }

    public static ChemblExportOptions defaults() {
        return new ChemblExportOptions(250_000, Long.MAX_VALUE, Selection.SEQUENTIAL, 0L, ChemblFilterOptions.defaults(), true);
    }

    public enum Selection { SEQUENTIAL, HASH }
}
