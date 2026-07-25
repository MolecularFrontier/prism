package tech.molecules.structurized.prismlite.swing.workspace.navigator;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismGroup;
import tech.molecules.structurized.prism.engine.PrismGroupMembership;
import tech.molecules.structurized.prism.engine.PrismGrouping;
import tech.molecules.structurized.prism.engine.PrismGroupingMode;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnGroupResolverTest {
    @Test
    void groupingFacetsHaveADedicatedNavigatorSection() throws Exception {
        PrismSession session = PrismSession.open(Path.of("..", "examples", "example.prismpack"));
        session.addGrouping(new PrismGrouping(
                "series-clusters",
                "Series clusters",
                "",
                null,
                PrismGroupingMode.EXCLUSIVE,
                List.of(new PrismGroup("cluster-a", "Series A", "", null, "CMPD-001", Map.of())),
                List.of(new PrismGroupMembership("CMPD-001", "cluster-a", 1.0, "representative", Map.of())),
                "series_cluster_id",
                Map.of()
        ));
        PrismLiteWorkspaceModel model = new PrismLiteWorkspaceModel(session);

        assertEquals(
                "Groupings",
                ColumnGroupResolver.groupFor(model, session.table().column("series_cluster_id"))
        );
    }
}
