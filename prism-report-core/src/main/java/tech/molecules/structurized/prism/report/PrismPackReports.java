package tech.molecules.structurized.prism.report;

import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.pack.PrismPack;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code .prism.md} reports carried inside a {@link PrismPack}.
 *
 * <p>Reports are stored using the pack's existing attachment facility: a zip entry under
 * {@code reports/} holding the markdown, plus an attachment record identifying it by MIME type.
 * Nothing in the pack format changes, so a reader that predates embedded reports simply ignores an
 * attachment it does not recognise.
 *
 * <p>This lives in {@code prism-report-core} rather than in {@code prism-engine} because report
 * parsing depends on view specifications from {@code prism-engine-ocl}; registering reports from
 * inside {@code PrismSession} would invert the module graph. Callers therefore attach explicitly
 * after opening a session - the caller is the one holding the pack, since {@code PrismSession}
 * retains no backing dataset.
 */
public final class PrismPackReports {
    /** MIME type marking an attachment as an embedded Prism report. */
    public static final String REPORT_MIME_TYPE = "text/markdown; profile=prism-report";

    /** Directory inside the pack holding embedded report sources. */
    public static final String REPORTS_DIRECTORY = "reports";

    private PrismPackReports() {
    }

    /** True when the attachment carries an embedded Prism report. */
    public static boolean isReport(PrismPack.Attachment attachment) {
        return attachment != null
                && attachment.mimeType() != null
                && attachment.mimeType().toLowerCase().contains("profile=prism-report");
    }

    /** Every report attachment in the pack, in pack order. */
    public static List<PrismPack.Attachment> reports(PrismPack pack) {
        if (pack == null || pack.attachments() == null) return List.of();
        return pack.attachments().attachments().stream().filter(PrismPackReports::isReport).toList();
    }

    /**
     * Registers every embedded report in {@code pack} as a view on {@code session}.
     *
     * <p>A report that fails validation does not abort the others, and does not prevent the dataset
     * from being used: its diagnostics are collected into {@code failures} and the next report is
     * attempted. This matters because a pack is still perfectly usable when one of its reports has
     * gone stale.
     *
     * @param failures optional sink for per-report validation failures; may be {@code null}
     * @return the view IDs registered, in order
     */
    public static List<String> attach(PrismPack pack,
                                      PrismSession session,
                                      List<PrismReportAttachmentFailure> failures) {
        ArrayList<String> registered = new ArrayList<>();
        for (PrismPack.Attachment attachment : reports(pack)) {
            String source = sourceOf(pack, attachment);
            if (source == null) {
                if (failures != null) {
                    failures.add(new PrismReportAttachmentFailure(attachment.id(),
                            "report attachment has no readable content", List.of()));
                }
                continue;
            }
            try {
                PrismViewRecord view = PrismReportRegistrar.register(
                        source,
                        // The registrar prefixes "report:" itself, so hand it the bare identity:
                        // the attachment ID already carries the prefix.
                        fallbackIdOf(attachment),
                        describe(pack, attachment),
                        pack.manifest() == null ? null : pack.manifest().id(),
                        session,
                        // An embedded report is by definition paired with this pack, so the
                        // authored-against comparison would be noise here.
                        null);
                session.addView(view);
                registered.add(view.id());
            } catch (PrismReportValidationException exception) {
                if (failures != null) {
                    failures.add(new PrismReportAttachmentFailure(attachment.id(),
                            exception.getMessage(), exception.diagnostics()));
                }
            }
        }
        return List.copyOf(registered);
    }

    public static List<String> attach(PrismPack pack, PrismSession session) {
        return attach(pack, session, null);
    }

    /**
     * Builds the attachment record for one report.
     *
     * <p>The source is stored <em>inline</em> in {@code content.text}. Storing it as a separate zip
     * entry under {@code reports/} would keep the markdown readable after an unzip, but
     * {@code PrismPackReader} carries {@code content.path} as a string and never resolves it to
     * file bytes, so honouring a path would mean extending {@code prism-core}'s reader. Inline
     * storage needs no format or reader change at all, which keeps embedded reports purely
     * additive: an older reader sees an attachment it does not recognise and ignores it.
     *
     * @param reportId stable report identity, used for the attachment ID
     * @param title    human-readable report title
     * @param source   complete {@code .prism.md} source
     * @param raw      extra attachment metadata, e.g. derived analysis requirements
     */
    public static PrismPack.Attachment attachmentFor(String reportId,
                                                     String title,
                                                     String source,
                                                     java.util.Map<String, Object> raw) {
        String slug = PrismReportRegistrar.slug(reportId);
        return new PrismPack.Attachment(
                "report:" + slug,
                new PrismPack.AttachmentTarget("dataset", null, null, null, java.util.Map.of()),
                title == null ? slug : title,
                REPORT_MIME_TYPE,
                new PrismPack.AttachmentContent("inline", source, null, java.util.Map.of()),
                raw == null ? java.util.Map.of() : java.util.Map.copyOf(raw));
    }

    /** Attachment identity with the {@code report:} prefix removed, if present. */
    private static String fallbackIdOf(PrismPack.Attachment attachment) {
        String id = attachment.id() == null ? attachment.name() : attachment.id();
        if (id == null) return null;
        return id.regionMatches(true, 0, "report:", 0, 7) ? id.substring(7) : id;
    }

    private static String sourceOf(PrismPack pack, PrismPack.Attachment attachment) {
        PrismPack.AttachmentContent content = attachment.content();
        if (content == null) return null;
        return content.text() == null || content.text().isBlank() ? null : content.text();
    }

    private static String describe(PrismPack pack, PrismPack.Attachment attachment) {
        return "prismpack:" + attachment.id();
    }
}
