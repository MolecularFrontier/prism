package tech.molecules.structurized.chembl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChemblSqliteSourceTest {
    @Test
    void streamsSelectedStructureColumns(@TempDir Path directory) throws Exception {
        Path database = directory.resolve("chembl.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE molecule_dictionary (molregno INTEGER, chembl_id TEXT, molecule_type TEXT, structure_type TEXT, polymer_flag INTEGER, inorganic_flag INTEGER)");
            statement.execute("CREATE TABLE compound_structures (molregno INTEGER, canonical_smiles TEXT, standard_inchi_key TEXT)");
            statement.execute("CREATE TABLE molecule_hierarchy (molregno INTEGER, parent_molregno INTEGER)");
            statement.execute("CREATE TABLE compound_records (molregno INTEGER, src_id INTEGER, doc_id INTEGER, record_id INTEGER)");
            statement.execute("INSERT INTO molecule_dictionary VALUES (0, 'CHEMBL_PARENT', 'Small molecule', 'MOL', 0, 0)");
            statement.execute("INSERT INTO molecule_dictionary VALUES (1, 'CHEMBL1', 'Small molecule', 'MOL', 0, 0)");
            statement.execute("INSERT INTO compound_structures VALUES (1, 'CCOc1ccc(N)cc1', 'KEY1')");
            statement.execute("INSERT INTO molecule_hierarchy VALUES (1, 0)");
            statement.execute("INSERT INTO compound_records VALUES (1, 7, 8, 9)");
        }

        try (ChemblSqliteSource source = new ChemblSqliteSource(database, "CHEMBL_37")) {
            assertTrue(source.hasNext());
            ChemblRecord record = source.next();
            assertEquals("CHEMBL1", record.chemblId());
            assertEquals("KEY1", record.inchiKey());
            assertEquals(1, record.sourceCount());
            assertFalse(source.hasNext());
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError();
    }
}
