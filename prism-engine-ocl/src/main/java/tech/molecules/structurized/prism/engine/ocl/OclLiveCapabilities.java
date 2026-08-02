package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.StereoMolecule;
import tech.molecules.structurized.prism.engine.live.PrismLiveCapability;
import tech.molecules.structurized.prism.engine.live.PrismLiveResult;

public final class OclLiveCapabilities {
    public static final PrismLiveCapability<StereoMolecule> DECODED_MOLECULE =
            new PrismLiveCapability<>(
                    "chemistry.ocl.decoded_molecule", "Decoded molecule",
                    "Canonical OpenChemLib molecule used by dependent live evaluators.",
                    StereoMolecule.class, false);

    public static final PrismLiveCapability<PrismLiveResult> BASIC_PROPERTIES =
            new PrismLiveCapability<>(
                    "chemistry.ocl.basic_properties", "Basic properties",
                    "Fast molecular descriptors calculated locally with OpenChemLib.",
                    PrismLiveResult.class, true);

    public static final PrismLiveCapability<PrismLiveResult> STRUCTURE_SUMMARY =
            new PrismLiveCapability<>(
                    "chemistry.ocl.structure_summary", "Structure summary",
                    "Structural counts and topology calculated locally with OpenChemLib.",
                    PrismLiveResult.class, true);

    private OclLiveCapabilities() {
    }
}
