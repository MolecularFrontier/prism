package tech.molecules.structurized.prism.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PrismOperationParameter(
        String id,
        PrismOperationParameterType type,
        String name,
        String description,
        boolean required,
        List<String> allowedValues,
        Map<String, Object> hints
) {
    public PrismOperationParameter {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("operation parameter id must not be blank");
        }
        id = id.trim();
        type = type == null ? PrismOperationParameterType.STRING : type;
        name = name == null || name.isBlank() ? id : name.trim();
        description = description == null ? "" : description.trim();
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        hints = hints == null || hints.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(hints));
    }

    public static PrismOperationParameter requiredColumn(String id, String name, String semanticType) {
        return new PrismOperationParameter(
                id,
                PrismOperationParameterType.COLUMN,
                name,
                "",
                true,
                List.of(),
                semanticType == null ? Map.of() : Map.of("semanticType", semanticType)
        );
    }

    public static PrismOperationParameter requiredString(String id, String name) {
        return new PrismOperationParameter(id, PrismOperationParameterType.STRING, name, "", true, List.of(), Map.of());
    }

    public static PrismOperationParameter optionalRowSet(String id, String name) {
        return new PrismOperationParameter(id, PrismOperationParameterType.ROW_SET, name, "", false, List.of(), Map.of());
    }

    public static PrismOperationParameter optionalColumnList(String id, String name, String semanticType) {
        return new PrismOperationParameter(
                id,
                PrismOperationParameterType.COLUMN_LIST,
                name,
                "",
                false,
                List.of(),
                semanticType == null ? Map.of() : Map.of("semanticType", semanticType)
        );
    }

    public static PrismOperationParameter requiredEnum(String id, String name, List<String> allowedValues) {
        return new PrismOperationParameter(id, PrismOperationParameterType.ENUM, name, "", true, allowedValues, Map.of());
    }
}
