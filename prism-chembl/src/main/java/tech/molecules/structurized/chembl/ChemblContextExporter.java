package tech.molecules.structurized.chembl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ChemblContextExporter {
    private static final String[] HEADER = {"chembl_id", "document_chembl_id", "source_id", "compound_key", "assay_chembl_id", "target_chembl_id"};

    public void export(Path database, Path moleculesTsv, Writer output) throws IOException {
        java.util.Set<String> selectedIds = new java.util.HashSet<>();
        try (var lines = java.nio.file.Files.lines(moleculesTsv, java.nio.charset.StandardCharsets.UTF_8)) {
            lines.skip(1).map(line -> line.split("\t", -1)).filter(columns -> columns.length > 0 && !columns[0].isBlank()).forEach(columns -> selectedIds.add(columns[0]));
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             PreparedStatement statement = connection.prepareStatement(query());
             ResultSet rows = statement.executeQuery();
             BufferedWriter writer = output instanceof BufferedWriter buffered ? buffered : new BufferedWriter(output)) {
            writer.write(String.join("\t", HEADER));
            writer.newLine();
            while (rows.next()) if (selectedIds.contains(rows.getString("chembl_id"))) {
                writer.write(String.join("\t", text(rows.getString("chembl_id")), text(rows.getString("document_chembl_id")), text(rows.getString("source_id")),
                        text(rows.getString("compound_key")), text(rows.getString("assay_chembl_id")), text(rows.getString("target_chembl_id"))));
                writer.newLine();
            }
            writer.flush();
        } catch (SQLException exception) {
            throw new IOException("Could not export ChEMBL context", exception);
        }
    }

    public void exportAll(Path database, Writer output) throws IOException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             PreparedStatement statement = connection.prepareStatement(query());
             ResultSet rows = statement.executeQuery();
             BufferedWriter writer = output instanceof BufferedWriter buffered ? buffered : new BufferedWriter(output)) {
            writer.write(String.join("\t", HEADER));
            writer.newLine();
            while (rows.next()) {
                writer.write(String.join("\t", text(rows.getString("chembl_id")), text(rows.getString("document_chembl_id")), text(rows.getString("source_id")),
                        text(rows.getString("compound_key")), text(rows.getString("assay_chembl_id")), text(rows.getString("target_chembl_id"))));
                writer.newLine();
            }
            writer.flush();
        } catch (SQLException exception) {
            throw new IOException("Could not export ChEMBL context", exception);
        }
    }

    private static String query() {
        return "SELECT md.chembl_id, d.doc_chembl_id AS document_chembl_id, cr.src_id AS source_id, cr.compound_key, " +
                "ass.assay_chembl_id, td.chembl_id AS target_chembl_id " +
                "FROM molecule_dictionary md JOIN compound_records cr ON cr.molregno = md.molregno " +
                "LEFT JOIN docs d ON d.doc_id = cr.doc_id " +
                "LEFT JOIN activities a ON a.record_id = cr.record_id " +
                "LEFT JOIN assays ass ON ass.assay_id = a.assay_id " +
                "LEFT JOIN target_dictionary td ON td.tid = a.tid ORDER BY md.molregno, cr.record_id, a.activity_id";
    }

    private static String text(String value) { return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' '); }
}
