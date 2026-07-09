package tech.molecules.structurized.prism.engine.ocl;

public final class OclComputedValueIds {
    private OclComputedValueIds() {
    }

    public static String molecule(String structureColumnId) {
        return "ocl.molecule:" + structureColumnId;
    }

    public static String ffp512(String structureColumnId) {
        return "ocl.ffp512:" + structureColumnId;
    }
}
