package tech.molecules.structurized.chembl;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class ChemblSqliteSource implements ChemblSource {
    private final Connection connection;
    private final PreparedStatement statement;
    private final ResultSet resultSet;
    private boolean hasNext;
    private boolean advanced;

    public ChemblSqliteSource(Path database, String release) throws IOException {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
            statement = connection.prepareStatement(query(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setFetchSize(1000);
            statement.setString(1, release == null ? "" : release);
            resultSet = statement.executeQuery();
        } catch (SQLException exception) {
            throw new IOException("Could not open ChEMBL SQLite database: " + database, exception);
        }
    }

    @Override
    public boolean hasNext() throws IOException {
        if (!advanced) {
            try { hasNext = resultSet.next(); advanced = true; }
            catch (SQLException exception) { throw new IOException("Could not read ChEMBL SQLite database", exception); }
        }
        return hasNext;
    }

    @Override
    public ChemblRecord next() throws IOException {
        if (!hasNext()) throw new IllegalStateException("no more ChEMBL molecules");
        try {
            ChemblRecord record = new ChemblRecord(resultSet.getString("chembl_id"), resultSet.getString("canonical_smiles"), resultSet.getString("standard_inchi_key"),
                    resultSet.getString("parent_chembl_id"), resultSet.getString("molecule_type"), resultSet.getString("structure_type"),
                    resultSet.getBoolean("polymer_flag"), resultSet.getBoolean("inorganic_flag"), resultSet.getString("release"),
                    resultSet.getInt("source_count"), resultSet.getInt("document_count"), resultSet.getString("document_ids"), resultSet.getString("source_ids"));
            advanced = false;
            return record;
        } catch (SQLException exception) { throw new IOException("Could not read ChEMBL molecule row", exception); }
    }

    @Override
    public void close() throws IOException {
        try { resultSet.close(); statement.close(); connection.close(); }
        catch (SQLException exception) { throw new IOException("Could not close ChEMBL SQLite database", exception); }
    }

    private static String query() {
        return "SELECT md.chembl_id, cs.canonical_smiles, cs.standard_inchi_key, " +
                "parent_md.chembl_id AS parent_chembl_id, md.molecule_type, md.structure_type, md.polymer_flag, md.inorganic_flag, " +
                "? AS release, COALESCE(cr.source_count, 0) AS source_count, COALESCE(cr.document_count, 0) AS document_count, " +
                "COALESCE(cr.document_ids, '') AS document_ids, COALESCE(cr.source_ids, '') AS source_ids " +
                "FROM molecule_dictionary md JOIN compound_structures cs ON cs.molregno = md.molregno " +
                "LEFT JOIN molecule_hierarchy mh ON mh.molregno = md.molregno LEFT JOIN molecule_dictionary parent_md ON parent_md.molregno = mh.parent_molregno " +
                "LEFT JOIN (SELECT molregno, COUNT(DISTINCT src_id) source_count, COUNT(DISTINCT doc_id) document_count, " +
                "GROUP_CONCAT(DISTINCT doc_id) document_ids, GROUP_CONCAT(DISTINCT src_id) source_ids FROM compound_records GROUP BY molregno) cr " +
                "ON cr.molregno = md.molregno WHERE cs.canonical_smiles IS NOT NULL ORDER BY md.molregno";
    }
}
