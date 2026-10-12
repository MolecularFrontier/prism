package tech.molecules.structurized.prism.report;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class PrismReportParseContext {
    private final String blockId;
    private final int sourceLine;
    private final List<PrismReportDiagnostic> diagnostics;

    PrismReportParseContext(String blockId, int sourceLine, List<PrismReportDiagnostic> diagnostics) {
        this.blockId = blockId;
        this.sourceLine = sourceLine;
        this.diagnostics = diagnostics;
    }

    public String blockId() { return blockId; }
    public int sourceLine() { return sourceLine; }

    public void rejectUnknownFields(JsonNode object, Set<String> allowed) {
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) error("UNKNOWN_FIELD", "Unknown field '" + field + "'.");
        }
    }

    public String requiredText(JsonNode json, String field) {
        String value = optionalText(json, field);
        if (value == null) error("MISSING_FIELD", "Report block requires '" + field + "'.");
        return value;
    }

    public String optionalText(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) {
            error("INVALID_FIELD_TYPE", field + " must be a string.");
            return null;
        }
        String text = value.textValue().trim();
        return text.isEmpty() ? null : text;
    }

    public boolean optionalBoolean(JsonNode json, String field, boolean fallback) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isBoolean()) {
            error("INVALID_FIELD_TYPE", field + " must be a boolean.");
            return fallback;
        }
        return value.booleanValue();
    }

    public int optionalInt(JsonNode json, String field, int fallback) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isIntegralNumber()) {
            error("INVALID_FIELD_TYPE", field + " must be an integer.");
            return fallback;
        }
        return value.intValue();
    }

    public Double optionalDouble(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
            error("INVALID_FIELD_TYPE", field + " must be a finite number.");
            return null;
        }
        return value.doubleValue();
    }

    public List<String> requiredStringArray(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            error("MISSING_FIELD", "Report block requires a non-empty '" + field + "' array.");
            return List.of();
        }
        ArrayList<String> result = new ArrayList<>();
        for (int index = 0; index < value.size(); index++) {
            JsonNode item = value.get(index);
            if (!item.isTextual() || item.textValue().isBlank()) {
                error("INVALID_FIELD_TYPE", field + "[" + index + "] must be a non-blank string.");
            } else {
                result.add(item.textValue().trim());
            }
        }
        return List.copyOf(result);
    }

    public void error(String code, String message) {
        diagnostics.add(new PrismReportDiagnostic(PrismReportSeverity.ERROR, code, message,
                blockId, sourceLine + 1, 1));
    }
}
