package tech.molecules.structurized.prism.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class PrismOperationParameterBinder {
    private PrismOperationParameterBinder() {
    }

    static Map<String, Object> bind(PrismOperationDescriptor descriptor,
                                    PrismSessionSnapshot snapshot,
                                    Map<String, Object> rawParameters) {
        Map<String, Object> raw = rawParameters == null ? Map.of() : rawParameters;
        Set<String> knownIds = descriptor.parameters().stream().map(PrismOperationParameter::id).collect(Collectors.toSet());
        for (String id : raw.keySet()) {
            if (!knownIds.contains(id)) {
                throw new PrismOperationException(
                        "UNKNOWN_PARAMETER",
                        "unknown parameter '" + id + "' for operation '" + descriptor.id() + "'",
                        id
                );
            }
        }
        LinkedHashMap<String, Object> bound = new LinkedHashMap<>();
        for (PrismOperationParameter parameter : descriptor.parameters()) {
            Object value = raw.get(parameter.id());
            if (isMissing(value)) {
                if (parameter.required()) {
                    throw new PrismOperationException(
                            "MISSING_PARAMETER",
                            "missing required parameter '" + parameter.id() + "'",
                            parameter.id()
                    );
                }
                continue;
            }
            bound.put(parameter.id(), convert(parameter, value, snapshot));
        }
        return Map.copyOf(bound);
    }

    private static Object convert(PrismOperationParameter parameter, Object value, PrismSessionSnapshot snapshot) {
        return switch (parameter.type()) {
            case STRING -> String.valueOf(value).trim();
            case BOOLEAN -> booleanValue(parameter, value);
            case NUMBER -> numberValue(parameter, value);
            case ENUM -> enumValue(parameter, value);
            case COLUMN -> columnValue(parameter, value, snapshot);
            case COLUMN_LIST -> columnListValue(parameter, value, snapshot);
            case ROW_SET -> rowSetValue(parameter, value, snapshot);
        };
    }

    private static Boolean booleanValue(PrismOperationParameter parameter, Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text)) {
            return true;
        }
        if ("false".equals(text)) {
            return false;
        }
        throw new PrismOperationException("INVALID_PARAMETER",
                "parameter '" + parameter.id() + "' must be true or false", parameter.id());
    }

    private static Double numberValue(PrismOperationParameter parameter, Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            throw new PrismOperationException("INVALID_PARAMETER",
                    "parameter '" + parameter.id() + "' must be numeric", parameter.id(), Map.of(), exception);
        }
    }

    private static String enumValue(PrismOperationParameter parameter, Object value) {
        String text = String.valueOf(value).trim();
        for (String allowed : parameter.allowedValues()) {
            if (allowed.equalsIgnoreCase(text)) {
                return allowed;
            }
        }
        throw new PrismOperationException(
                "INVALID_PARAMETER",
                "parameter '" + parameter.id() + "' must be one of " + parameter.allowedValues(),
                parameter.id(),
                Map.of("allowedValues", parameter.allowedValues())
        );
    }

    private static String columnValue(PrismOperationParameter parameter, Object value, PrismSessionSnapshot snapshot) {
        String columnId = String.valueOf(value).trim();
        validateColumn(parameter, columnId, snapshot);
        return columnId;
    }

    private static List<String> columnListValue(PrismOperationParameter parameter, Object value, PrismSessionSnapshot snapshot) {
        ArrayList<String> columnIds = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                addColumnValue(parameter, item, snapshot, columnIds);
            }
        } else {
            for (String item : String.valueOf(value).split(",")) {
                addColumnValue(parameter, item, snapshot, columnIds);
            }
        }
        return List.copyOf(columnIds);
    }

    private static String rowSetValue(PrismOperationParameter parameter, Object value, PrismSessionSnapshot snapshot) {
        String rowSetId = String.valueOf(value).trim();
        if (snapshot.rowSet(rowSetId).isEmpty()) {
            throw new PrismOperationException(
                    "UNKNOWN_ROW_SET",
                    "unknown row set '" + rowSetId + "'",
                    parameter.id(),
                    Map.of("rowSetId", rowSetId)
            );
        }
        return rowSetId;
    }

    private static void addColumnValue(PrismOperationParameter parameter, Object value, PrismSessionSnapshot snapshot, List<String> columnIds) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        String columnId = String.valueOf(value).trim();
        validateColumn(parameter, columnId, snapshot);
        if (!columnIds.contains(columnId)) {
            columnIds.add(columnId);
        }
    }

    private static void validateColumn(PrismOperationParameter parameter, String columnId, PrismSessionSnapshot snapshot) {
        PrismColumn column = snapshot.table().findColumn(columnId).orElseThrow(() -> new PrismOperationException(
                "UNKNOWN_COLUMN",
                "unknown column '" + columnId + "'",
                parameter.id(),
                Map.of("columnId", columnId)
        ));
        Object semanticHint = parameter.hints().get("semanticType");
        if (semanticHint != null && !String.valueOf(semanticHint).isBlank()) {
            String requiredSemantic = String.valueOf(semanticHint);
            boolean compatible = requiredSemantic.equals(column.schema().semanticType())
                    || ("chemical_structure".equals(requiredSemantic) && column.type() == PrismColumnType.MOLECULE);
            if (!compatible) {
                throw new PrismOperationException(
                        "INCOMPATIBLE_COLUMN",
                        "column '" + columnId + "' is not compatible with parameter '" + parameter.id() + "'",
                        parameter.id(),
                        Map.of("columnId", columnId, "requiredSemanticType", requiredSemantic)
                );
            }
        }
    }

    private static boolean isMissing(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }
}
