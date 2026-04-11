package tech.molecules.structurized.prism.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointFetchRequestTest {

    @Test
    void requestDefaultsCollectionsToEmptyImmutableLists() {
        EndpointFetchRequest request = EndpointFetchRequest.builder().build();

        assertEquals(0, request.getSubjectIds().size());
        assertEquals(0, request.getEndpointIds().size());
        assertThrows(UnsupportedOperationException.class, () -> request.getSubjectIds().add("cmp-1"));
    }
}
