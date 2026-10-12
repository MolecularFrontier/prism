package tech.molecules.structurized.prism.report;

import java.util.List;
import java.util.Map;

public record PrismReportBlockSchema(
        String type,
        String description,
        List<PrismReportFieldSchema> fields,
        Map<String, Object> example
) {
    public PrismReportBlockSchema {
        fields = fields == null ? List.of() : List.copyOf(fields);
        example = example == null ? Map.of() : Map.copyOf(example);
    }
}
