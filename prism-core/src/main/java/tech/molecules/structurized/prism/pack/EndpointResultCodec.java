package tech.molecules.structurized.prism.pack;

import tech.molecules.structurized.prism.model.EndpointDataType;
import tech.molecules.structurized.prism.result.AbstractEndpointResult;
import tech.molecules.structurized.prism.result.BooleanResult;
import tech.molecules.structurized.prism.result.CategoricalResult;
import tech.molecules.structurized.prism.result.EndpointResult;
import tech.molecules.structurized.prism.result.NumericResult;
import tech.molecules.structurized.prism.result.NumericState;
import tech.molecules.structurized.prism.result.OptionalNumericResult;
import tech.molecules.structurized.prism.result.OptionalNumericState;
import tech.molecules.structurized.prism.result.PrismNumericDatapoint;
import tech.molecules.structurized.prism.result.TextResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Canonical, lossless JSON representation of an {@link EndpointResult}. */
public final class EndpointResultCodec {
    private EndpointResultCodec() {}

    public static Map<String, Object> encode(EndpointResult result) {
        Objects.requireNonNull(result, "result must not be null");
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("type", result.getType().name());
        put(map, "n", result.getN());
        if (!result.getRawValueIds().isEmpty()) map.put("rawValueIds", result.getRawValueIds());
        put(map, "firstMeasurement", result.getFirstMeasurement());
        put(map, "lastMeasurement", result.getLastMeasurement());
        if (!result.getDetails().isEmpty()) map.put("details", result.getDetails());

        if (result instanceof NumericResult numeric) {
            map.put("state", numeric.getState().name());
            put(map, "mean", numeric.getMean());
            put(map, "lower", numeric.getLower());
            put(map, "upper", numeric.getUpper());
            if (!numeric.getRawValues().isEmpty()) map.put("rawValues", numeric.getRawValues());
            if (!numeric.getDatapoints().isEmpty()) map.put("datapoints", encodeDatapoints(numeric.getDatapoints()));
        } else if (result instanceof OptionalNumericResult numeric) {
            map.put("state", numeric.getState().name());
            put(map, "mean", numeric.getMean());
            put(map, "lower", numeric.getLower());
            put(map, "upper", numeric.getUpper());
            if (!numeric.getRawValues().isEmpty()) map.put("rawValues", numeric.getRawValues());
            if (!numeric.getDatapoints().isEmpty()) map.put("datapoints", encodeDatapoints(numeric.getDatapoints()));
        } else if (result instanceof BooleanResult value) {
            map.put("value", value.getValue());
        } else if (result instanceof CategoricalResult value) {
            map.put("value", value.getValue());
        } else if (result instanceof TextResult value) {
            map.put("text", value.getText());
        } else {
            throw new IllegalArgumentException("Unsupported endpoint result class: " + result.getClass().getName());
        }
        return Map.copyOf(map);
    }

    public static EndpointResult decode(Map<String, Object> map) {
        Objects.requireNonNull(map, "endpoint result payload must not be null");
        EndpointDataType type = enumValue(EndpointDataType.class, requiredString(map, "type"), "type");
        return switch (type) {
            case NUMERIC -> decodeNumeric(map);
            case OPTIONAL_NUMERIC -> applyCommon(OptionalNumericResult.builder()
                    .state(enumValue(OptionalNumericState.class, requiredString(map, "state"), "state"))
                    .mean(doubleValue(map.get("mean"), "mean"))
                    .lower(doubleValue(map.get("lower"), "lower"))
                    .upper(doubleValue(map.get("upper"), "upper"))
                    .rawValues(doubleList(map.get("rawValues"), "rawValues"))
                    .datapoints(decodeDatapoints(map.get("datapoints"))), map).build();
            case BOOLEAN -> applyCommon(BooleanResult.builder().value(booleanValue(map.get("value"), "value")), map).build();
            case CATEGORICAL -> applyCommon(CategoricalResult.builder().value(requiredString(map, "value")), map).build();
            case TEXT -> applyCommon(TextResult.builder().text(requiredString(map, "text")), map).build();
        };
    }

    private static NumericResult decodeNumeric(Map<String, Object> map) {
        NumericState state = enumValue(NumericState.class, requiredString(map, "state"), "state");
        NumericResult.Builder builder = NumericResult.builder().state(state)
                .lower(doubleValue(map.get("lower"), "lower"))
                .upper(doubleValue(map.get("upper"), "upper"))
                .rawValues(doubleList(map.get("rawValues"), "rawValues"))
                .datapoints(decodeDatapoints(map.get("datapoints")));
        Double mean = doubleValue(map.get("mean"), "mean");
        if (mean != null) builder.mean(mean);
        return applyCommon(builder, map).build();
    }

    public static String encodeJson(EndpointResult result) {
        return PrismPackJson.stringifyCompact(encode(result));
    }

    public static EndpointResult decodeJson(String json) {
        Object parsed = PrismPackJson.parse(json);
        if (!(parsed instanceof Map<?, ?> raw)) throw new IllegalArgumentException("Endpoint result JSON must be an object");
        return decode(stringMap(raw));
    }

    private static <B extends AbstractEndpointResult.Builder<B>> B applyCommon(B builder, Map<String, Object> map) {
        Number n = number(map.get("n"), "n");
        return builder.n(n == null ? null : n.intValue())
                .rawValueIds(stringList(map.get("rawValueIds"), "rawValueIds"))
                .firstMeasurement(string(map.get("firstMeasurement"), "firstMeasurement"))
                .lastMeasurement(string(map.get("lastMeasurement"), "lastMeasurement"))
                .details(mapValue(map.get("details"), "details"));
    }

    private static List<Map<String, Object>> encodeDatapoints(List<PrismNumericDatapoint> datapoints) {
        return datapoints.stream().map(point -> {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            put(map, "date", point.getDate()); put(map, "batch", point.getBatch()); put(map, "sourceId", point.getSourceId());
            put(map, "value", point.getValue()); put(map, "unprocessedValue", point.getUnprocessedValue());
            if (!point.getMetadata().isEmpty()) map.put("metadata", point.getMetadata());
            return Map.copyOf(map);
        }).toList();
    }

    private static List<PrismNumericDatapoint> decodeDatapoints(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("datapoints must be an array");
        List<PrismNumericDatapoint> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) throw new IllegalArgumentException("datapoints entries must be objects");
            Map<String, Object> map = stringMap(raw);
            result.add(PrismNumericDatapoint.builder()
                    .date(string(map.get("date"), "date"))
                    .batch(string(map.get("batch"), "batch"))
                    .sourceId(string(map.get("sourceId"), "sourceId"))
                    .value(doubleValue(map.get("value"), "value"))
                    .unprocessedValue(string(map.get("unprocessedValue"), "unprocessedValue"))
                    .metadata(mapValue(map.get("metadata"), "metadata"))
                    .build());
        }
        return List.copyOf(result);
    }

    private static void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
    private static String requiredString(Map<String, Object> map, String key) {
        String value = string(map.get(key), key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " must be a non-blank string");
        return value;
    }
    private static String string(Object value, String field) {
        if (value == null) return null;
        if (!(value instanceof String text)) throw new IllegalArgumentException(field + " must be a string");
        return text;
    }
    private static Number number(Object value, String field) {
        if (value == null) return null;
        if (!(value instanceof Number number)) throw new IllegalArgumentException(field + " must be a number");
        return number;
    }
    private static Double doubleValue(Object value, String field) { Number number = number(value, field); return number == null ? null : number.doubleValue(); }
    private static boolean booleanValue(Object value, String field) {
        if (!(value instanceof Boolean bool)) throw new IllegalArgumentException(field + " must be a boolean");
        return bool;
    }
    private static List<String> stringList(Object value, String field) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(field + " must be an array");
        List<String> result = new ArrayList<>();
        for (Object item : list) result.add(string(item, field));
        return List.copyOf(result);
    }
    private static List<Double> doubleList(Object value, String field) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException(field + " must be an array");
        List<Double> result = new ArrayList<>();
        for (Object item : list) result.add(doubleValue(item, field));
        return List.copyOf(result);
    }
    private static Map<String, Object> mapValue(Object value, String field) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException(field + " must be an object");
        return stringMap(map);
    }
    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return Map.copyOf(result);
    }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Unsupported " + field + ": " + value, error); }
    }
}
