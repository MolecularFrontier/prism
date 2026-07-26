package tech.molecules.structurized.chembl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;

public final class ChemblTsvWriter implements AutoCloseable {
    public static final String[] HEADER = {
            "chembl_id", "chembl_smiles", "smiles", "idcode", "inchi_key", "parent_chembl_id",
            "num_non_h_atoms", "num_ring_atoms", "num_rings", "num_aromatic_rings", "largest_ring_size",
            "num_rotatable_bonds", "num_hetero_atoms", "formal_charge", "molecular_weight", "clogp", "tpsa",
            "source_count", "document_count", "document_ids", "source_ids", "release", "num_fragments", "quality_flags"
    };
    private final BufferedWriter writer;

    public ChemblTsvWriter(Writer writer) throws IOException {
        this.writer = writer instanceof BufferedWriter buffered ? buffered : new BufferedWriter(writer);
        this.writer.write(String.join("	", HEADER));
        this.writer.newLine();
    }

    public void write(ChemblStructureRow row) throws IOException {
        ChemblProperties p = row.properties();
        ChemblRecord s = row.source();
        String[] values = {
                text(row.chemblId()), text(row.sourceSmiles()), text(row.smiles()), text(row.idcode()), text(s.inchiKey()), text(row.parentChemblId()),
                Integer.toString(p.nonHydrogenAtoms()), Integer.toString(p.ringAtoms()), Integer.toString(p.rings()), Integer.toString(p.aromaticRings()),
                Integer.toString(p.largestRingSize()), Integer.toString(p.rotatableBonds()), Integer.toString(p.heteroAtoms()), Integer.toString(p.formalCharge()),
                format(p.molecularWeight()), format(p.clogp()), format(p.tpsa()), Integer.toString(s.sourceCount()), Integer.toString(s.documentCount()),
                text(s.documentIds()), text(s.sourceIds()), text(s.release()), Integer.toString(row.fragmentCount()), ""
        };
        writer.write(String.join("	", values));
        writer.newLine();
    }

    public void flush() throws IOException { writer.flush(); }
    @Override public void close() throws IOException { writer.close(); }

    private static String format(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.5f", value) : ""; }
    private static String text(String value) { return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '); }
}
