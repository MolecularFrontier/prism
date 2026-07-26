package tech.molecules.structurized.chembl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

public final class ChemblStructuresCli {
    private ChemblStructuresCli() {
    }

    public static void main(String[] args) {
        try {
            int exitCode = run(args);
            if (exitCode != 0) System.exit(exitCode);
        } catch (Exception exception) {
            System.err.println("ERROR: " + exception.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args) throws IOException {
        if (args.length == 0 || "--help".equals(args[0]) || "help".equals(args[0])) {
            printHelp();
            return 0;
        }
        if ("enrich-context".equals(args[0])) return enrichContext(args);
        if (!"fetch".equals(args[0])) throw new IllegalArgumentException("unknown command: " + args[0]);
        Arguments options = new Arguments(Arrays.copyOfRange(args, 1, args.length));
        Path output = Path.of(options.required("output"));
        ChemblExportOptions exportOptions = options.exportOptions();
        try (ChemblSource source = options.source();
             Writer outputWriter = outputWriter(output);
             ChemblTsvWriter writer = new ChemblTsvWriter(outputWriter)) {
            ChemblExportStats stats = new ChemblStructureExporter().export(source, exportOptions, writer);
            System.err.println(stats);
        }
        return 0;
    }

    private static int enrichContext(String[] args) throws IOException {
        Arguments options = new Arguments(Arrays.copyOfRange(args, 1, args.length));
        Path database = Path.of(options.required("database"));
        Path output = Path.of(options.required("output"));
        String molecules = options.value("molecules");
        try (Writer writer = outputWriter(output)) {
            if (molecules == null) new ChemblContextExporter().exportAll(database, writer);
            else new ChemblContextExporter().export(database, Path.of(molecules), writer);
        }
        return 0;
    }

    private static Writer outputWriter(Path output) throws IOException {
        if ("-".equals(output.toString())) return new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
        if (output.toString().endsWith(".gz")) return new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)), StandardCharsets.UTF_8));
        return Files.newBufferedWriter(output, StandardCharsets.UTF_8);
    }

    private static void printHelp() {
        System.out.println("chembl-structures fetch --source sqlite --database chembl_XX.db --output structures.tsv");
        System.out.println("chembl-structures fetch --source api --output structures.tsv --max-accepted 250000");
        System.out.println("chembl-structures enrich-context --database chembl_XX.db --molecules structures.tsv --output context.tsv");
    }

    private static final class Arguments {
        private final String[] args;
        private final Set<String> names = new LinkedHashSet<>(Arrays.asList(
                "source", "molecules", "database", "output", "release", "max-accepted", "max-scanned", "min-heavy-atoms", "max-heavy-atoms", "min-charge", "max-charge", "selection", "seed", "page-size", "retries"));

        private Arguments(String[] args) { this.args = args; }

        private String required(String name) {
            String value = value(name);
            if (value == null || value.isBlank()) throw new IllegalArgumentException("--" + name + " is required");
            return value;
        }

        private String value(String name) {
            String option = "--" + name;
            for (int i = 0; i < args.length - 1; i++) if (option.equals(args[i])) return args[i + 1];
            return null;
        }

        private ChemblSource source() throws IOException {
            String source = value("source");
            if (source == null || source.equalsIgnoreCase("sqlite")) return new ChemblSqliteSource(Path.of(required("database")), value("release"));
            if (source.equalsIgnoreCase("api")) return new ChemblApiSource(value("release"), integer("page-size", 1000), integer("retries", 4));
            throw new IllegalArgumentException("source must be sqlite or api");
        }

        private ChemblExportOptions exportOptions() {
            ChemblFilterOptions defaults = ChemblFilterOptions.defaults();
            return new ChemblExportOptions(longValue("max-accepted", 250_000), longValue("max-scanned", Long.MAX_VALUE),
                    "hash".equalsIgnoreCase(value("selection")) ? ChemblExportOptions.Selection.HASH : ChemblExportOptions.Selection.SEQUENTIAL,
                    longValue("seed", 0), new ChemblFilterOptions(integer("min-heavy-atoms", defaults.minHeavyAtoms()), integer("max-heavy-atoms", defaults.maxHeavyAtoms()),
                            integer("min-charge", defaults.minCharge()), integer("max-charge", defaults.maxCharge()), defaults.allowedElements()), true);
        }

        private int integer(String name, int fallback) { String value = value(name); return value == null ? fallback : Integer.parseInt(value); }
        private long longValue(String name, long fallback) { String value = value(name); return value == null ? fallback : Long.parseLong(value); }
    }
}
