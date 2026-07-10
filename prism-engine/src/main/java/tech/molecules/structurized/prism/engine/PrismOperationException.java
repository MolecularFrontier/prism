package tech.molecules.structurized.prism.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PrismOperationException extends RuntimeException {
    private final String errorCode;
    private final String parameterName;
    private final Map<String, Object> details;

    public PrismOperationException(String errorCode, String message) {
        this(errorCode, message, null, Map.of(), null);
    }

    public PrismOperationException(String errorCode, String message, String parameterName) {
        this(errorCode, message, parameterName, Map.of(), null);
    }

    public PrismOperationException(String errorCode, String message, String parameterName, Map<String, Object> details) {
        this(errorCode, message, parameterName, details, null);
    }

    public PrismOperationException(String errorCode,
                                   String message,
                                   String parameterName,
                                   Map<String, Object> details,
                                   Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode == null || errorCode.isBlank() ? "OPERATION_ERROR" : errorCode;
        this.parameterName = parameterName;
        this.details = details == null || details.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public String errorCode() {
        return errorCode;
    }

    public String parameterName() {
        return parameterName;
    }

    public Map<String, Object> details() {
        return details;
    }
}
