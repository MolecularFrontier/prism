package tech.molecules.structurized.prismlite.swing;

import com.formdev.flatlaf.FlatLightLaf;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.pack.PrismPack;
import tech.molecules.structurized.prism.pack.PrismPackReader;
import tech.molecules.structurized.prism.report.PrismPackReports;
import tech.molecules.structurized.prism.report.PrismReportAttachmentFailure;
import tech.molecules.structurized.prismlite.swing.report.PrismReportLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PrismLiteApplication {
    private static final String REPORT_OPTION = "--report";

    private PrismLiteApplication() {
    }

    /**
     * Usage: {@code PrismLiteApplication [<dataset>] [--report <file.prism.md>]...}
     *
     * <p>The dataset is a {@code .prismpack} or PRISM TSV snapshot directory. Each
     * {@code --report} is validated against that dataset, registered as a view and, for the first
     * one, focused on startup. This exists so an agent can hand over a single runnable command
     * rather than expecting the recipient to load the report through the menu.
     */
    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        FlatLightLaf.setup();

        Path datasetPath = arguments.datasetPath() == null ? defaultExamplePath() : arguments.datasetPath();

        // Read the pack ourselves rather than using PrismSession.open, so that reports embedded in
        // it can be registered. PrismSession retains no backing dataset, so the pack has to be held
        // here; and report parsing cannot live in prism-engine without inverting the module graph.
        PrismPack pack = isPack(datasetPath) ? PrismPackReader.read(datasetPath) : null;
        PrismSession session = pack == null ? PrismSession.open(datasetPath) : PrismSession.from(pack);
        PrismLiteFrame frame = PrismLiteFrame.open(session, datasetPath);

        String firstViewId = null;
        if (pack != null) {
            List<PrismReportAttachmentFailure> failures = new ArrayList<>();
            List<String> embedded = PrismPackReports.attach(pack, session, failures);
            for (PrismReportAttachmentFailure failure : failures) {
                System.err.println("embedded report " + failure.attachmentId()
                        + " could not be loaded: " + failure.message());
            }
            if (!embedded.isEmpty()) firstViewId = embedded.getFirst();
        }
        for (Path reportPath : arguments.reportPaths()) {
            // A report that fails validation must not take the whole GUI down: the dataset is
            // already open and useful on its own.
            try {
                var report = PrismReportLoader.load(reportPath, datasetPath, session);
                session.addView(report);
                if (firstViewId == null) firstViewId = report.id();
            } catch (Exception exception) {
                System.err.println("failed to load report " + reportPath + ": " + exception.getMessage());
            }
        }
        if (firstViewId != null) frame.focusLoadedView(firstViewId);
    }

    private static boolean isPack(Path path) {
        return path != null && path.getFileName() != null
                && path.getFileName().toString().toLowerCase().endsWith(".prismpack");
    }

    private record Arguments(Path datasetPath, List<Path> reportPaths) {
        static Arguments parse(String[] args) {
            Path dataset = null;
            List<Path> reports = new ArrayList<>();
            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if (REPORT_OPTION.equals(argument)) {
                    if (index + 1 >= args.length) {
                        throw new IllegalArgumentException(REPORT_OPTION + " requires a path");
                    }
                    reports.add(Path.of(args[++index]));
                } else if (argument.startsWith(REPORT_OPTION + "=")) {
                    reports.add(Path.of(argument.substring(REPORT_OPTION.length() + 1)));
                } else if (dataset == null) {
                    dataset = Path.of(argument);
                } else {
                    throw new IllegalArgumentException("unexpected argument: " + argument);
                }
            }
            return new Arguments(dataset, List.copyOf(reports));
        }
    }

    private static Path defaultExamplePath() {
        Path fromModuleDirectory = Path.of("examples", "moonshot-medchem.prismpack");
        if (fromModuleDirectory.toFile().exists()) {
            return fromModuleDirectory;
        }
        return Path.of("examples", "example.prismpack");
    }
}
