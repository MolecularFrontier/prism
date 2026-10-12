package tech.molecules.structurized.prism.engine.snapshot;

import tech.molecules.structurized.prism.engine.PrismRowSet;
import tech.molecules.structurized.prism.engine.PrismTable;
import tech.molecules.structurized.prism.score.EndpointScoreDefinition;

import java.util.List;
import java.util.Optional;

/** Common read-side contract for repository-derived and standalone Prism snapshots. */
public interface PrismSnapshotDataset {
    PrismTable table();
    List<PrismSnapshotEndpoint> endpoints();
    List<PrismRowSet> rowSets();
    List<EndpointScoreDefinition> scoreDefinitions();
    Optional<PrismEndpointCell> endpointCell(String rowId, String endpointId);
    PrismSnapshotCapabilities capabilities();
    Optional<PrismSnapshotOrigin> origin();
}
