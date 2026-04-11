package tech.molecules.structurized.prism.result;

import tech.molecules.structurized.prism.model.EndpointDataType;

import java.util.List;
import java.util.Map;

/**
 * Base protocol contract for one endpoint payload.
 */
public interface EndpointResult {
    EndpointDataType getType();

    Integer getN();

    List<String> getRawValueIds();

    String getFirstMeasurement();

    String getLastMeasurement();

    Map<String, Object> getDetails();
}
