package tech.molecules.structurized.prismlite.swing.workspace.navigator;

import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import java.util.Locale;

public final class ColumnGroupResolver {
    private ColumnGroupResolver() {
    }

    public static String groupFor(PrismLiteWorkspaceModel model, PrismColumn column) {
        String role = normalize(column.schema().role());
        String semantic = normalize(column.schema().semanticType());
        if (role.contains("grouping_facet") || semantic.contains("group_membership")) {
            return "Groupings";
        }
        if (model.isComputedColumn(column.id())) {
            return "Computed";
        }
        if (role.contains("identifier") || semantic.contains("compound_id") || semantic.contains("external_id")) {
            return "Identity";
        }
        if (column.type() == PrismColumnType.MOLECULE || semantic.contains("chemical_structure")) {
            return "Structure";
        }
        if (semantic.contains("endpoint")) {
            return "Endpoints";
        }
        if (semantic.contains("descriptor")) {
            return "Descriptors";
        }
        if (column.type() == PrismColumnType.BOOLEAN || semantic.contains("flag")) {
            return "Flags";
        }
        if (column.type() == PrismColumnType.CATEGORICAL || semantic.contains("category")) {
            return "Categories";
        }
        return "Other";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
