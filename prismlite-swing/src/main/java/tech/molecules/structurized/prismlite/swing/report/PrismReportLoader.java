package tech.molecules.structurized.prismlite.swing.report;

import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.report.PrismReportDatasetIdentity;
import tech.molecules.structurized.prism.report.PrismReportRegistrar;
import tech.molecules.structurized.prism.report.PrismReportValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads a {@code .prism.md} report from disk and registers it on a session.
 *
 * <p>A thin path-based wrapper over {@link PrismReportRegistrar}, which holds the shared
 * parse/validate/register implementation used equally by embedded reports.
 */
public final class PrismReportLoader {
    private PrismReportLoader() {
    }

    public static PrismViewRecord load(Path reportPath, Path datasetPath, PrismSession session)
            throws IOException, PrismReportValidationException {
        return load(reportPath, datasetPath, session, null);
    }

    public static PrismViewRecord load(Path reportPath,
                                       Path datasetPath,
                                       PrismSession session,
                                       PrismReportDatasetIdentity datasetIdentity)
            throws IOException, PrismReportValidationException {
        return PrismReportRegistrar.register(
                Files.readString(reportPath),
                filenameId(reportPath),
                reportPath.toAbsolutePath().normalize().toString(),
                datasetPath == null ? null : datasetPath.toAbsolutePath().normalize().toString(),
                session,
                datasetIdentity);
    }

    private static String filenameId(Path path) {
        String name = path.getFileName() == null ? "report" : path.getFileName().toString();
        if (name.toLowerCase().endsWith(".prism.md")) name = name.substring(0, name.length() - 9);
        return name;
    }
}
