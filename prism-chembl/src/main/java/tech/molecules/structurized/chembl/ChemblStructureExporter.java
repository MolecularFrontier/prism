package tech.molecules.structurized.chembl;

import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.IsomericSmilesCreator;
import com.actelion.research.chem.StereoMolecule;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class ChemblStructureExporter {
    private final ChemblNormalizer normalizer;
    private final ChemblPropertyCalculator propertyCalculator;

    public ChemblStructureExporter() {
        this(new ChemblNormalizer(), new ChemblPropertyCalculator());
    }

    ChemblStructureExporter(ChemblNormalizer normalizer, ChemblPropertyCalculator propertyCalculator) {
        this.normalizer = normalizer;
        this.propertyCalculator = propertyCalculator;
    }

    public ChemblExportStats export(ChemblSource source, ChemblExportOptions options, ChemblTsvWriter writer) throws IOException {
        ChemblExportStats stats = new ChemblExportStats();
        Set<String> idcodes = new HashSet<>();
        while (stats.scannedCount() < options.maxScanned() && stats.acceptedCount() < options.maxAccepted() && source.hasNext()) {
            ChemblRecord record = source.next();
            stats.scanned();
            if (!selected(record.chemblId(), options)) continue;
            try {
                validateMetadata(record);
                NormalizedMolecule normalized = normalizer.normalize(record.smiles(), options.filter());
                StereoMolecule molecule = normalized.molecule();
                String idcode = new Canonizer(molecule).getIDCode();
                if (!idcodes.add(idcode)) {
                    stats.reject(ChemblRejection.DUPLICATE);
                    continue;
                }
                ChemblProperties properties = propertyCalculator.calculate(molecule);
                writer.write(new ChemblStructureRow(record.chemblId(), options.includeSourceSmiles() ? normalized.sourceSmiles() : "",
                        IsomericSmilesCreator.createSmiles(molecule), idcode, record.inchiKey(), record.parentChemblId(), properties, record,
                        normalized.originalFragmentCount()));
                stats.accepted();
            } catch (ChemblNormalizationException exception) {
                stats.reject(exception.reason());
            } catch (RuntimeException exception) {
                stats.reject(ChemblRejection.PARSE_FAILURE);
            }
        }
        writer.flush();
        return stats;
    }

    private static void validateMetadata(ChemblRecord record) {
        if (record.smiles() == null) throw new ChemblNormalizationException(ChemblRejection.NO_USABLE_STRUCTURE, "no canonical SMILES");
        if (record.moleculeType() != null && !"small molecule".equalsIgnoreCase(record.moleculeType()))
            throw new ChemblNormalizationException(ChemblRejection.WRONG_MOLECULE_TYPE, record.moleculeType());
        if (record.structureType() != null && !"mol".equalsIgnoreCase(record.structureType()))
            throw new ChemblNormalizationException(ChemblRejection.WRONG_STRUCTURE_TYPE, record.structureType());
        if (record.polymer()) throw new ChemblNormalizationException(ChemblRejection.POLYMER, "polymer molecule");
        if (record.inorganic()) throw new ChemblNormalizationException(ChemblRejection.INORGANIC, "inorganic molecule");
    }

    private static boolean selected(String chemblId, ChemblExportOptions options) {
        if (options.selection() == ChemblExportOptions.Selection.SEQUENTIAL) return true;
        long hash = HashSelection.hash(chemblId, options.seed());
        return (hash & 1L) == 0L;
    }
}
