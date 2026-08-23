package tech.molecules.structurized.prism.report;

import com.fasterxml.jackson.databind.JsonNode;
import tech.molecules.structurized.prism.engine.PrismSession;

import java.util.List;

public interface PrismReportBlockProvider {
    String type();
    PrismReportBlock parse(JsonNode json, PrismReportParseContext context);
    void validate(PrismReportBlock block, PrismSession session, List<PrismReportDiagnostic> diagnostics);
}
