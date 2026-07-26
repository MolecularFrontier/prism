package tech.molecules.structurized.chembl;

import java.util.EnumMap;
import java.util.Map;

public final class ChemblExportStats {
    private long scanned;
    private long accepted;
    private final EnumMap<ChemblRejection, Long> rejections = new EnumMap<>(ChemblRejection.class);

    public void scanned() { scanned++; }
    public void accepted() { accepted++; }
    public void reject(ChemblRejection reason) { rejections.merge(reason, 1L, Long::sum); }
    public long scannedCount() { return scanned; }
    public long acceptedCount() { return accepted; }
    public Map<ChemblRejection, Long> rejections() { return Map.copyOf(rejections); }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("Scanned: ").append(scanned).append(System.lineSeparator())
                .append("Accepted: ").append(accepted);
        for (ChemblRejection reason : ChemblRejection.values()) {
            Long count = rejections.get(reason);
            if (count != null && count > 0) result.append(System.lineSeparator()).append(reason).append(": ").append(count);
        }
        return result.toString();
    }
}
