package tech.molecules.structurized.prism.engine.ocl.io;

import static org.junit.jupiter.api.Assertions.*;

import com.actelion.research.chem.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tech.molecules.structurized.prism.engine.*;
import tech.molecules.structurized.prism.engine.ocl.*;
import tech.molecules.structurized.prism.pack.*;

class DwarImporterTest {
  @TempDir Path temp;

  static String table(String count, String properties, String names, String rows) {
    return "<datawarrior-fileinfo>\n<version=\"3.3\">\n"
        + (count == null ? "" : "<rowcount=\"" + count + "\">\n")
        + "</datawarrior-fileinfo>\n"
        + properties
        + names
        + "\n"
        + rows;
  }

  static DwarImporter.Result read(String text) throws IOException {
    return DwarImporter.read(new StringReader(text), "synthetic.dwar", null, null);
  }

  static String property(String name, String type, String parent) {
    return "<columnName=\""
        + name
        + "\">\n<columnProperty=\"specialType\t"
        + type
        + "\">\n"
        + (parent == null ? "" : "<columnProperty=\"parent\t" + parent + "\">\n");
  }

  static String[] molecule(String smiles, boolean fragment) throws Exception {
    StereoMolecule mol = new StereoMolecule();
    new SmilesParser().parse(mol, smiles);
    mol.setFragment(fragment);
    if (fragment) mol.setAtomQueryFeature(0, Molecule.cAtomQFAromatic, true);
    Canonizer c = new Canonizer(mol);
    return new String[] {c.getIDCode(), c.getEncodedCoordinates()};
  }

  @Test
  void ordinaryTablesNeedNoEndpointsChemistryOrUniqueScientificIdentifier() throws Exception {
    var result =
        read(
            table(
                null,
                "",
                "ID\tValue\tFlag\tCategory\tComment",
                "00123\t3.1\ttrue\tA\tline<NL>two<TAB>part\n"
                    + "00123\t4.2\tfalse\tA\t\n"
                    + "00123\t\tTRUE\tA\t\n"
                    + "00123\t1e-2\t\tA\t\n"));
    var p = result.pack();
    assertNull(p.endpoints());
    assertNull(p.molecules());
    assertEquals(4, p.dataFrame().rows().size());
    assertEquals(
        List.of("text", "numeric", "boolean", "categorical", "text"),
        p.schema().columns().stream().map(PrismPack.Column::type).toList());
    assertEquals("line\ntwo\tpart", p.dataFrame().valueAt(0, "dwar_c5"));
    var ids = RowIdIndex.forTable(PrismSession.from(p).table());
    assertEquals("row:0", ids.rowId(0));
    assertEquals("row:1", ids.rowId(1));
    assertEquals(-1, result.report().declaredRows());
    assertTrue(result.report().issues().stream().anyMatch(i -> i.code().equals("IDENTIFIER_TEXT")));
  }

  @Test
  void completeColumnsPreserveBoundsMultivaluesMixedEntriesAndLargeIdentifiers() throws Exception {
    String text =
        table(
            "2",
            "",
            "Bound\tMany\tMixed\tHuge\tLate",
            "< 3.7\t3.7; 5.1\t2.5\t9007199254740993\t1\n"
                + "7.2\t4\tfailed\t9007199254740994\tnot numeric\n");
    var r = read(text);
    assertTrue(r.pack().schema().columns().stream().allMatch(c -> c.type().equals("text")));
    assertEquals("< 3.7", r.pack().dataFrame().valueAt(0, "dwar_c1"));
    assertEquals("3.7; 5.1", r.pack().dataFrame().valueAt(0, "dwar_c2"));
    assertTrue(r.report().issues().stream().anyMatch(i -> i.code().equals("CENSORED_TEXT")));
    assertTrue(r.report().issues().stream().anyMatch(i -> i.code().equals("MULTIVALUE_TEXT")));
    assertThrows(
        DwarImporter.ImportException.class,
        () ->
            DwarImporter.read(
                new StringReader(text),
                "x",
                new DwarImporter.Options(Map.of("dwar_c1", "numeric")),
                null));
    var override =
        DwarImporter.read(
            new StringReader(table("2", "", "Value", "1\n2\n")),
            "x",
            new DwarImporter.Options(Map.of("dwar_c1", "text")),
            null);
    assertEquals("text", override.pack().schema().columns().getFirst().type());
    assertThrows(
        DwarImporter.ImportException.class,
        () ->
            DwarImporter.read(
                new StringReader(text),
                "x",
                new DwarImporter.Options(Map.of("unknown", "text")),
                null));
  }

  @Test
  void allStructureParentsCoordinatesQueryAndStereoSurvivePackAndComputedRegistration()
      throws Exception {
    var a = molecule("N[C@@H](C)C(=O)O", false);
    var b = molecule("c1ccccc1", true);
    String properties =
        "<column properties>\n"
            + property("B_coords", "idcoordinates2D", "B")
            + property("A", "idcode", null)
            + property("A_coords", "idcoordinates2D", "A")
            + property("B", "idcode", null)
            + property("cache", "FragFp", "A")
            + property("conformer", "idcoordinates3D", "B")
            + "<columnName=\"Stored\">\n"
            + "<columnProperty=\"formula\tDO_NOT_EXECUTE()\">\n"
            + "</column properties>\n";
    var p =
        read(table(
                "3",
                properties,
                "B_coords\tA\tStored\tA_coords\tB\tcache\tconformer",
                b[1]
                    + "\t"
                    + a[0]
                    + "\t2.5\t"
                    + a[1]
                    + "\t"
                    + b[0]
                    + "\tbogus cache\topaque3D\n\t"
                    + a[0]
                    + "\t3.5\t\t\t\t\n"
                    + "\t\t\t\t\t\t\n"
                    + "<datawarrior properties>\n"
                    + "<macro=\"ignore\">\n"
                    + "</datawarrior properties>\n"))
            .pack();
    assertEquals(
        List.of("dwar_c1", "dwar_c4", "dwar_c6", "dwar_c7"), p.tableView().hiddenColumns());
    assertEquals("dwar_c4", p.schema().columns().get(1).raw().get("coordinatesColumn"));
    assertEquals("dwar_c1", p.schema().columns().get(4).raw().get("coordinatesColumn"));
    var file = temp.resolve("copy.prismpack");
    PrismPackWriter.write(file, p);
    var copy = PrismPackReader.read(file);
    assertEquals(p.dataFrame(), copy.dataFrame());
    assertEquals(
        ((Map<?, ?>)
                ((Map<?, ?>) p.schema().columns().get(2).raw().get("sourceMetadata"))
                    .get("datawarrior"))
            .get("properties"),
        ((Map<?, ?>)
                ((Map<?, ?>) copy.schema().columns().get(2).raw().get("sourceMetadata"))
                    .get("datawarrior"))
            .get("properties"));
    assertNull(copy.endpoints());
    var session = PrismSession.from(copy);
    OclPrismEngineSupport.registerCapabilities(session);
    for (var entry : Map.of("dwar_c2", a, "dwar_c5", b).entrySet()) {
      StereoMolecule expected =
          new IDCodeParser(false).getCompactMolecule(entry.getValue()[0], entry.getValue()[1]);
      StereoMolecule actual =
          session
              .computedValues()
              .value(OclComputedValueIds.molecule(entry.getKey()), 0, StereoMolecule.class);
      assertEquals(expected.isFragment(), actual.isFragment());
      assertEquals(entry.getValue()[0], new Canonizer(actual).getIDCode());
      for (int atom = 0; atom < actual.getAllAtoms(); atom++) {
        assertEquals(expected.getAtomX(atom), actual.getAtomX(atom), 1e-9);
        assertEquals(expected.getAtomY(atom), actual.getAtomY(atom), 1e-9);
      }
      assertNotNull(
          session
              .computedValues()
              .value(OclComputedValueIds.ffp512(entry.getKey()), 0, long[].class));
    }
    assertNull(
        session
            .computedValues()
            .value(OclComputedValueIds.molecule("dwar_c5"), 1, StereoMolecule.class));
  }

  @Test
  void declaredCountsAreAdvisoryAndDuplicateNamesKeepPhysicalOrder() throws Exception {
    var result = read(table("999999999", "", "Same\tSame", "x\tx\nx\tx\n"));
    assertEquals(2, result.report().rows());
    assertEquals(2, result.report().columns());
    assertEquals(
        List.of("Same", "Same"),
        result.report().decisions().stream().map(DwarImporter.ColumnDecision::name).toList());
    assertTrue(
        result.report().issues().stream().anyMatch(i -> i.code().equals("ROW_COUNT_MISMATCH")));
    assertEquals("x", result.pack().dataFrame().valueAt(1, "dwar_c2"));
  }

  @Test
  void malformedTruncatedAndFailedReadersCannotBecomeSuccessfulEmptyImports() {
    assertThrows(DwarImporter.ImportException.class, () -> read("not a DWAR file"));
    var truncated =
        assertThrows(DwarImporter.ImportException.class, () -> read(table("2", "", "A\tB", "")));
    assertEquals("TRUNCATED_DWAR", truncated.code());
    assertEquals(
        "MALFORMED_ROW",
        assertThrows(
                DwarImporter.ImportException.class, () -> read(table("2", "", "A\tB", "x\ty\nz\n")))
            .code());
    Reader failure =
        new Reader() {
          public int read(char[] b, int o, int n) throws IOException {
            throw new IOException("intentional read failure");
          }

          public void close() {}
        };
    assertThrows(
        DwarImporter.ImportException.class, () -> DwarImporter.read(failure, "broken", null, null));
  }

  @Test
  void aReadFailureAfterValidRecordsIsReportedRatherThanPublishingAPartialTable() {
    Reader failing =
        new Reader() {
          final StringReader delegate = new StringReader(table("1", "", "A", "x\n"));

          public int read(char[] chars, int offset, int length) throws IOException {
            int count = delegate.read(chars, offset, length);
            if (count < 0) throw new IOException("failure after readable rows");
            return count;
          }

          public void close() {
            delegate.close();
          }
        };
    var failure =
        assertThrows(
            DwarImporter.ImportException.class,
            () -> DwarImporter.read(failing, "broken.dwar", null, null));
    assertEquals("READ_FAILURE", failure.code());
  }

  @Test
  void emptyAndTextOnlyTablesAreLegitimateWithoutNumericOrChemistryColumns() throws Exception {
    var empty = read(table("0", "", "Name", ""));
    assertEquals(0, PrismSession.from(empty.pack()).table().rowCount());
    var text = read(table("2", "", "Name", "first\nsecond\n"));
    assertEquals("text", text.report().decisions().getFirst().type());
    assertEquals(2, PrismSession.from(text.pack()).table().rowCount());
  }

  @Test
  void cancellationIsObservedDuringParsingAndTheReaderIsClosed() {
    boolean[] closed = {false};
    var reader =
        new StringReader(table("1", "", "A", "x\n")) {
          @Override
          public void close() {
            closed[0] = true;
            super.close();
          }
        };
    assertThrows(
        CancellationException.class,
        () ->
            DwarImporter.read(
                reader,
                "cancel",
                null,
                new DwarImporter.Progress() {
                  public void checkCancelled() {
                    throw new CancellationException();
                  }
                }));
    assertTrue(closed[0]);
  }

  @Test
  void suppliedWikipediaFixtureOpensWithoutInventingEndpoints() throws Exception {
    Path fixture =
        Path.of(
                System.getenv()
                    .getOrDefault(
                        "HYDRA_DWAR_FIXTURE", "../reference_data/Wikipedia_Compounds_01.dwar"))
            .toAbsolutePath();
    // Maven may run this module with its own directory as cwd.
    if (!Files.isRegularFile(fixture) && System.getenv("HYDRA_DWAR_FIXTURE") == null)
      fixture = Path.of("../../reference_data/Wikipedia_Compounds_01.dwar");
    Assumptions.assumeTrue(
        Files.isRegularFile(fixture),
        "Set HYDRA_DWAR_FIXTURE to exercise the external Wikipedia fixture");
    var r = DwarImporter.read(fixture, null, null);
    assertEquals(24903, r.report().rows());
    assertEquals(22, r.report().columns());
    assertEquals(1, r.report().structureColumns());
    assertNull(r.pack().endpoints());
    assertEquals("dwar_c1", r.pack().schema().columns().get(2).raw().get("coordinatesColumn"));
    assertEquals(24903, PrismSession.from(r.pack()).table().rowCount());
  }
}
