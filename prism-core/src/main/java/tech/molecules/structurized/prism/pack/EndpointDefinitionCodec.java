package tech.molecules.structurized.prism.pack;

import tech.molecules.structurized.prism.model.CategoryDefinition;
import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.model.EndpointDefinition;
import tech.molecules.structurized.prism.model.EndpointType;
import tech.molecules.structurized.prism.model.EvaluationMode;
import tech.molecules.structurized.prism.model.NumericEndpointMeta;
import tech.molecules.structurized.prism.model.NumericScale;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Canonical PrismPack representation of a complete endpoint definition. */
public final class EndpointDefinitionCodec {
    private EndpointDefinitionCodec() {}

    public static Map<String, Object> encode(EndpointDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("id", definition.getId()); map.put("name", definition.getName()); map.put("path", definition.getPath());
        map.put("datatype", definition.getDatatype().name()); map.put("endpointType", definition.getEndpointType().name());
        put(map, "unit", definition.getUnit()); map.put("evaluationMode", definition.getEvaluationMode().name());
        put(map, "description", definition.getDescription());
        if (definition.getNumericMeta() != null) {
            LinkedHashMap<String, Object> numeric = new LinkedHashMap<>();
            put(numeric, "scale", definition.getNumericMeta().getScale() == null ? null : definition.getNumericMeta().getScale().name());
            put(numeric, "domainLowerBound", definition.getNumericMeta().getDomainLowerBound());
            put(numeric, "domainUpperBound", definition.getNumericMeta().getDomainUpperBound());
            map.put("numericMeta", numeric);
        }
        if (!definition.getCategories().isEmpty()) map.put("categories", definition.getCategories().stream().map(category -> {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>(); item.put("id", category.getId()); item.put("name", category.getName());
            put(item, "description", category.getDescription()); return Map.copyOf(item);
        }).toList());
        return Map.copyOf(map);
    }

    public static EndpointDefinition decode(Map<String, Object> map) {
        EndpointDefinition.Builder builder = EndpointDefinition.builder()
                .id(required(map, "id")).name(required(map, "name")).path(required(map, "path"))
                .datatype(EndpointDataType.valueOf(required(map, "datatype")))
                .endpointType(EndpointType.valueOf(required(map, "endpointType")))
                .unit(string(map.get("unit"))).evaluationMode(EvaluationMode.valueOf(required(map, "evaluationMode")))
                .description(string(map.get("description")));
        Object numericValue = map.get("numericMeta");
        if (numericValue instanceof Map<?, ?> raw) {
            Map<String, Object> numeric = stringMap(raw);
            String scale = string(numeric.get("scale"));
            builder.numericMeta(NumericEndpointMeta.builder()
                    .scale(scale == null ? null : NumericScale.valueOf(scale))
                    .domainLowerBound(number(numeric.get("domainLowerBound")))
                    .domainUpperBound(number(numeric.get("domainUpperBound"))).build());
        }
        Object categoriesValue = map.get("categories");
        if (categoriesValue instanceof List<?> categories) {
            List<CategoryDefinition> decoded = new ArrayList<>();
            for (Object item : categories) {
                if (!(item instanceof Map<?, ?> raw)) throw new IllegalArgumentException("categories entries must be objects");
                Map<String, Object> category = stringMap(raw);
                decoded.add(CategoryDefinition.builder().id(required(category, "id")).name(required(category, "name"))
                        .description(string(category.get("description"))).build());
            }
            builder.categories(decoded);
        }
        return builder.build();
    }

    private static void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
    private static String required(Map<String, Object> map, String key) {
        String value = string(map.get(key)); if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required"); return value;
    }
    private static String string(Object value) { if (value == null) return null; if (value instanceof String text) return text; throw new IllegalArgumentException("Expected string, got " + value); }
    private static Double number(Object value) { if (value == null) return null; if (value instanceof Number n) return n.doubleValue(); throw new IllegalArgumentException("Expected number, got " + value); }
    private static Map<String, Object> stringMap(Map<?, ?> raw) { LinkedHashMap<String, Object> map = new LinkedHashMap<>(); raw.forEach((k, v) -> map.put(String.valueOf(k), v)); return map; }
}
