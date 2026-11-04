package tech.molecules.structurized.prism.engine.ocl.io;

import com.actelion.research.chem.io.DWARFileParser;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;
import tech.molecules.structurized.prism.pack.PrismPack;

/**
 * OCL-backed scientific table import. No UI, endpoint fabrication or DataWarrior runtime
 * dependency.
 */
public final class DwarImporter {
  private DwarImporter() {}

  public interface Progress {
    default void update(long rows, long declaredRows, String message) {}

    default void checkCancelled() {
      if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
  }

  public record Options(Map<String, String> columnTypes) {
    public Options {
      columnTypes = columnTypes == null ? Map.of() : Map.copyOf(columnTypes);
    }

    public static Options defaults() {
      return new Options(Map.of());
    }
  }

  public record Issue(String severity, String code, String columnId, long count, String message) {}

  public record ColumnDecision(
      String id,
      String name,
      int sourceIndex,
      String type,
      boolean technical,
      String coordinatesColumn,
      Map<String, String> sourceProperties) {}

  public record Report(
      String format,
      String sourceName,
      long rows,
      int columns,
      int ordinaryColumns,
      int structureColumns,
      int technicalColumns,
      long declaredRows,
      List<ColumnDecision> decisions,
      List<Issue> issues) {
    public Map<String, Object> asMap() {
      var out = new LinkedHashMap<String, Object>();
      out.put("format", format);
      out.put("sourceName", sourceName);
      out.put("rows", rows);
      out.put("columns", columns);
      out.put("ordinaryColumns", ordinaryColumns);
      out.put("structureColumns", structureColumns);
      out.put("technicalColumns", technicalColumns);
      out.put("declaredRows", declaredRows);
      out.put(
          "decisions",
          decisions.stream()
              .map(
                  c -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("id", c.id());
                    m.put("name", c.name());
                    m.put("sourceIndex", c.sourceIndex());
                    m.put("type", c.type());
                    m.put("technical", c.technical());
                    m.put("coordinatesColumn", c.coordinatesColumn());
                    m.put("sourceProperties", c.sourceProperties());
                    return m;
                  })
              .toList());
      out.put(
          "issues",
          issues.stream()
              .map(
                  i -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("severity", i.severity());
                    m.put("code", i.code());
                    m.put("columnId", i.columnId());
                    m.put("count", i.count());
                    m.put("message", i.message());
                    return m;
                  })
              .toList());
      return out;
    }
  }

  public record Result(PrismPack pack, Report report) {}

  public static final class ImportException extends IOException {
    private final String code;

    public ImportException(String code, String message, Throwable cause) {
      super(message, cause);
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  public static Result read(Path file, Options options, Progress progress) throws IOException {
    return read(
        Files.newBufferedReader(file, StandardCharsets.UTF_8),
        file.getFileName().toString(),
        options,
        progress);
  }

  /** Owns and closes the supplied reader, including when OCL absorbs a reader failure. */
  public static Result read(Reader input, String sourceName, Options options, Progress progress)
      throws IOException {
    Objects.requireNonNull(input);
    var opts = options == null ? Options.defaults() : options;
    var feedback = progress == null ? new Progress() {} : progress;
    var reader = new ObservedReader(input, feedback);
    DWARFileParser parser = null;
    try {
      parser =
          new DWARFileParser(
              reader,
              DWARFileParser.MODE_COORDINATES_REQUIRE_2D
                  | DWARFileParser.MODE_BUFFER_HEAD_AND_TAIL);
      if (!parser.isOpen() || parser.getFieldNames() == null || parser.getHeadOrTail() == null)
        throw new ImportException(
            "MALFORMED_DWAR", "OpenChemLib could not read a complete DWAR header", reader.failure);
      var head = new ArrayList<>(parser.getHeadOrTail());
      if (head.isEmpty())
        throw new ImportException("MALFORMED_DWAR", "Missing DWAR column header", null);
      String[] names = head.getLast().split("\\t", -1);
      int width = names.length;
      if (width == 1 && names[0].isBlank())
        throw new ImportException("MALFORMED_DWAR", "Empty DWAR column header", null);
      var special = parser.getSpecialFieldMap();
      int ordinary = 0;
      var ordinaryIndex = new int[width];
      Arrays.fill(ordinaryIndex, -1);
      var properties = new ArrayList<Map<String, String>>();
      var issues = new ArrayList<Issue>();
      var seenNames = new HashSet<String>();
      for (int c = 0; c < width; c++) {
        if (!seenNames.add(names[c]))
          issues.add(
              new Issue(
                  "warning",
                  "DUPLICATE_NAME",
                  id(c),
                  1,
                  "Duplicate source name retained with a separate technical column ID"));
        var field = special.get(names[c]);
        if (field == null) {
          if (ordinary >= parser.getFieldNames().length
              || !names[c].equals(parser.getFieldNames()[ordinary]))
            throw new ImportException(
                "MALFORMED_DWAR", "Inconsistent ordinary-field mapping at column " + (c + 1), null);
          ordinaryIndex[c] = ordinary++;
        } else if (field.fieldIndex != c) {
          throw new ImportException(
              "MALFORMED_DWAR", "Ambiguous special-field mapping at column " + (c + 1), null);
        }
        var props = new TreeMap<String, String>();
        var sourceProps = parser.getColumnProperties(names[c]);
        if (sourceProps != null)
          for (String key : sourceProps.stringPropertyNames())
            props.put(key, sourceProps.getProperty(key));
        properties.add(Collections.unmodifiableMap(props));
      }
      long declared = parser.getRowCount();
      var rows = new ArrayList<List<String>>();
      while (parser.next()) {
        feedback.checkCancelled();
        // OCL tolerates short/long records and may retain a previous cell on a short row.
        // Validate its raw current record width before using the reader's decoded accessors.
        String raw = parser.getRow();
        if (raw == null || 1 + raw.chars().filter(ch -> ch == '\t').count() != width)
          throw new ImportException(
              "MALFORMED_ROW",
              "DWAR record " + (rows.size() + 1) + " does not contain " + width + " fields",
              null);
        var row = new ArrayList<String>(width);
        for (int c = 0; c < width; c++)
          row.add(
              Objects.requireNonNullElse(
                  ordinaryIndex[c] >= 0
                      ? parser.getFieldData(ordinaryIndex[c])
                      : parser.getSpecialFieldData(c),
                  ""));
        rows.add(List.copyOf(row));
        if (rows.size() % 256 == 0) feedback.update(rows.size(), declared, "Reading DWAR records");
      }
      if (reader.failure != null)
        throw new ImportException(
            "READ_FAILURE", "DWAR input ended because its reader failed", reader.failure);
      if (declared > 0 && rows.isEmpty())
        throw new ImportException(
            "TRUNCATED_DWAR", "DWAR declares records but contains no readable data rows", null);
      if (declared >= 0 && declared != rows.size())
        issues.add(
            new Issue(
                "warning",
                "ROW_COUNT_MISMATCH",
                null,
                1,
                "Declared row count "
                    + declared
                    + " differs from "
                    + rows.size()
                    + " imported records; the declaration is advisory and the source may be"
                    + " incomplete"));
      feedback.update(rows.size(), declared, "Interpreting complete columns");
      var decisions = new ArrayList<ColumnDecision>();
      var columns = new ArrayList<PrismPack.Column>();
      var visible = new ArrayList<String>();
      var hidden = new ArrayList<String>();
      String primary = null, primaryCoordinates = null;
      int structures = 0, technical = 0;
      for (int c = 0; c < width; c++) {
        feedback.checkCancelled();
        var field = special.get(names[c]);
        boolean structure = field != null && "idcode".equals(field.type);
        boolean hiddenField = field != null && !structure;
        String coords = null;
        if (structure) {
          structures++;
          if (primary == null) primary = id(c);
          for (int j = 0; j < width; j++) {
            var child = special.get(names[j]);
            if (child != null
                && "idcoordinates2D".equals(child.type)
                && names[c].equals(child.parent)) {
              if (coords == null) coords = id(j);
              else
                issues.add(
                    new Issue(
                        "warning",
                        "MULTIPLE_2D_COORDINATES",
                        id(c),
                        1,
                        "Multiple 2D coordinate fields retained; first source-order child used for"
                            + " depiction"));
            }
          }
          if (primary.equals(id(c))) primaryCoordinates = coords;
        }
        String type = structure ? "structure" : hiddenField ? "text" : infer(rows, c, issues);
        String override = opts.columnTypes().get(id(c));
        if (override != null) {
          if (field != null)
            throw new ImportException(
                "INVALID_TYPE", "Special chemistry fields cannot be retyped: " + id(c), null);
          validateType(rows, c, override);
          type = override;
          issues.add(
              new Issue(
                  "info",
                  "TYPE_OVERRIDE",
                  id(c),
                  rows.size(),
                  "Explicit caller interpretation: " + type));
        }
        var sourceMetadata = new LinkedHashMap<String, Object>();
        sourceMetadata.put("originalName", names[c]);
        sourceMetadata.put("sourceIndex", c);
        sourceMetadata.put("properties", properties.get(c));
        if (field != null) {
          sourceMetadata.put("specialType", field.type);
          if (field.parent != null) sourceMetadata.put("parent", field.parent);
        }
        var raw = new LinkedHashMap<String, Object>();
        raw.put("sourceMetadata", Map.of("datawarrior", sourceMetadata));
        if (coords != null) raw.put("coordinatesColumn", coords);
        if (hiddenField) {
          technical++;
          hidden.add(id(c));
          raw.put("hidden", true);
          issues.add(
              new Issue(
                  "info",
                  "SPECIAL_FIELD_RETAINED",
                  id(c),
                  rows.size(),
                  "idcoordinates2D".equals(field.type)
                      ? "Original 2D coordinates retained as a hidden source column and associated"
                          + " with their parent structure"
                      : "Encoded "
                          + field.type
                          + " content retained as a hidden source column; imported descriptor"
                          + " caches, reactions, 3D and annotations are not executed or"
                          + " interpreted"));
        } else visible.add(id(c));
        columns.add(
            new PrismPack.Column(
                id(c),
                type,
                structure ? "molecule" : null,
                names[c],
                hiddenField ? "source_technical" : structure ? "structure" : "data",
                null,
                null,
                null,
                structure ? "idcode" : null,
                raw));
        decisions.add(
            new ColumnDecision(id(c), names[c], c, type, hiddenField, coords, properties.get(c)));
      }
      for (String overridden : opts.columnTypes().keySet())
        if (columns.stream().noneMatch(c -> c.name().equals(overridden)))
          throw new ImportException("INVALID_TYPE", "Unknown column override: " + overridden, null);
      var tail = new ArrayList<String>();
      for (String line : parser.getHeadOrTail()) if (line != null) tail.add(line);
      if (!tail.isEmpty()
          || head.stream().anyMatch(l -> l.contains("macroList") || l.contains("explanation")))
        issues.add(
            new Issue(
                "info",
                "SOURCE_CONFIGURATION_NOT_APPLIED",
                null,
                1,
                "Source explanation, lists, details, layouts, plots, filters and macros retained as"
                    + " metadata only; source analysis configuration is not applied"));
      issues.add(
          new Issue(
              "info",
              "POSITIONAL_ROW_IDENTITIES",
              null,
              rows.size(),
              "Prism assigns row:<source position> IDs within this import. Source identifiers and"
                  + " duplicate structures remain separate rows; IDs do not reconcile independently"
                  + " imported or reordered files"));
      var report =
          new Report(
              "dwar",
              Objects.requireNonNullElse(sourceName, "Imported DWAR"),
              rows.size(),
              width,
              ordinary,
              structures,
              technical,
              declared,
              List.copyOf(decisions),
              List.copyOf(issues));
      var provenance = new LinkedHashMap<String, Object>();
      provenance.put("sourceFormat", "dwar");
      provenance.put("importReport", report.asMap());
      provenance.put(
          "sourceMetadata",
          Map.of(
              "datawarrior",
              Map.of("headerLines", List.copyOf(head), "footerLines", List.copyOf(tail))));
      var moleculeMetadata = new LinkedHashMap<String, Object>();
      if (primaryCoordinates != null) moleculeMetadata.put("coordinatesColumn", primaryCoordinates);
      var pack =
          new PrismPack(
              new PrismPack.Manifest(
                  "0.3",
                  UUID.randomUUID().toString(),
                  report.sourceName(),
                  "Scientific table imported with OpenChemLib DWARFileParser",
                  Instant.now().toString(),
                  "Prism OCL DWAR importer",
                  new PrismPack.DataframeRef(
                      "main",
                      "data/dataframe.tsv",
                      "schema/dataframe.schema.json",
                      "record",
                      Map.of()),
                  primary == null ? null : "semantics/molecules.json",
                  null,
                  "views/table.json",
                  null,
                  null,
                  "provenance/provenance.json",
                  Map.of()),
              new PrismPack.DataFrame(columns.stream().map(PrismPack.Column::name).toList(), rows),
              new PrismPack.DataFrameSchema(columns, Map.of()),
              primary == null
                  ? null
                  : new PrismPack.MoleculeMetadata(primary, "idcode", null, moleculeMetadata),
              null,
              new PrismPack.TableView(
                  "imported",
                  "Imported scientific table",
                  visible,
                  List.of(),
                  hidden,
                  List.of(),
                  List.of(),
                  List.of(),
                  Map.of()),
              null,
              null,
              provenance,
              List.of());
      feedback.checkCancelled();
      feedback.update(rows.size(), rows.size(), "Scientific table prepared");
      return new Result(pack, report);
    } catch (ImportException | CancellationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ImportException(
          "MALFORMED_DWAR",
          "OpenChemLib could not interpret the DWAR input: "
              + Objects.toString(e.getMessage(), e.getClass().getSimpleName()),
          e);
    } finally {
      if (parser != null) parser.close();
      reader.close();
    }
  }

  private static String id(int index) {
    return "dwar_c" + (index + 1);
  }

  private static final String NUMBER = "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?";
  private static final Pattern NUMERIC = Pattern.compile("(?:[<>]=?\\s*)?" + NUMBER);
  private static final Pattern LEADING_ZERO = Pattern.compile("[+-]?0\\d.*");

  private static String infer(List<List<String>> rows, int col, List<Issue> issues) {
    long nonempty = 0, numeric = 0, bool = 0, multiple = 0, identifiers = 0, bounds = 0;
    boolean integer = true;
    var distinct = new HashSet<String>();
    for (var row : rows) {
      String raw = row.get(col), v = raw.trim();
      if (v.isEmpty()) continue;
      nonempty++;
      if (distinct.size() < 51) distinct.add(raw);
      if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) bool++;
      boolean identifierLike = LEADING_ZERO.matcher(v).matches();
      if (v.contains(";") || v.contains("\n")) {
        for (String part : v.split("[;\\n]", -1))
          if (NUMERIC.matcher(part.trim()).matches()) {
            multiple++;
            break;
          }
      }
      if (NUMERIC.matcher(v).matches()) {
        String exact = v.replaceFirst("^[<>]=?\\s*", "");
        double number = Double.parseDouble(exact);
        if (Double.isFinite(number)) {
          numeric++;
          integer &= number == Math.rint(number);
          if (v.startsWith("<") || v.startsWith(">")) bounds++;
          if (number == Math.rint(number)
              && new BigDecimal(exact).abs().compareTo(new BigDecimal("9007199254740991")) > 0)
            identifierLike = true;
        }
      }
      if (identifierLike) identifiers++;
    }
    String id = id(col);
    if (identifiers > 0) {
      issues.add(
          new Issue(
              "info",
              "IDENTIFIER_TEXT",
              id,
              identifiers,
              "Leading-zero or large integral values retained as text to preserve"
                  + " identifiers/precision"));
      return "text";
    }
    if (multiple > 0) {
      issues.add(
          new Issue(
              "warning",
              "MULTIVALUE_TEXT",
              id,
              multiple,
              "Numeric multivalue cells retained verbatim as text; no averaging or entry removal"));
      return "text";
    }
    if (bounds > 0) {
      issues.add(
          new Issue(
              "warning",
              "CENSORED_TEXT",
              id,
              bounds,
              "Qualified numeric cells retained verbatim as text: Prism scalar columns require"
                  + " exact values. No bound is treated as an exact observation"));
      return "text";
    }
    if (nonempty > 0 && numeric == nonempty) {
      return integer ? "integer" : "numeric";
    }
    if (numeric > 0 || bool > 0 && bool != nonempty) {
      issues.add(
          new Issue(
              "warning",
              "MIXED_VALUES_TEXT",
              id,
              nonempty,
              "Complete-column validation found mixed values; all source text retained"));
      return "text";
    }
    if (nonempty > 0 && bool == nonempty) return "boolean";
    return nonempty > 0 && distinct.size() <= 50 && distinct.size() * 4 <= nonempty
        ? "categorical"
        : "text";
  }

  private static void validateType(List<List<String>> rows, int col, String type)
      throws ImportException {
    if (!Set.of("numeric", "integer", "text", "categorical", "boolean").contains(type))
      throw new ImportException("INVALID_TYPE", "Unsupported type override: " + type, null);
    if (type.equals("text") || type.equals("categorical")) return;
    for (int r = 0; r < rows.size(); r++) {
      String v = rows.get(r).get(col).trim();
      if (v.isEmpty()) continue;
      boolean valid =
          type.equals("boolean")
              ? v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")
              : NUMERIC.matcher(v).matches();
      if (valid && !type.equals("boolean")) {
        if (v.startsWith("<") || v.startsWith(">")) valid = false;
        double d = Double.parseDouble(v.replaceFirst("^[<>]=?\\s*", ""));
        valid = valid && Double.isFinite(d) && (!type.equals("integer") || d == Math.rint(d));
      }
      if (!valid)
        throw new ImportException(
            "INVALID_TYPE",
            "Type "
                + type
                + " does not fit record "
                + (r + 1)
                + ", column "
                + id(col)
                + "; source was not imported",
            null);
    }
  }

  private static final class ObservedReader extends BufferedReader {
    final Progress progress;
    IOException failure;
    boolean first = true;

    ObservedReader(Reader reader, Progress progress) {
      super(reader);
      this.progress = progress;
    }

    @Override
    public String readLine() throws IOException {
      progress.checkCancelled();
      try {
        String line = super.readLine();
        if (first) {
          first = false;
          if (line != null && line.startsWith("\uFEFF")) line = line.substring(1);
        }
        return line;
      } catch (IOException e) {
        failure = e;
        throw e;
      }
    }
  }
}
