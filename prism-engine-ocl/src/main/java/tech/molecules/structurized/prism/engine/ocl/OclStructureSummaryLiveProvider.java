package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.RingCollection;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.prediction.PropertyCalculator;
import tech.molecules.structurized.prism.engine.live.PrismLiveCapability;
import tech.molecules.structurized.prism.engine.live.PrismLiveComputationContext;
import tech.molecules.structurized.prism.engine.live.PrismLiveComputationProvider;
import tech.molecules.structurized.prism.engine.live.PrismLiveInput;
import tech.molecules.structurized.prism.engine.live.PrismLiveResult;
import tech.molecules.structurized.prism.engine.live.PrismMoleculeLiveInput;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

final class OclStructureSummaryLiveProvider implements PrismLiveComputationProvider<PrismLiveResult> {
    @Override
    public PrismLiveCapability<PrismLiveResult> capability() {
        return OclLiveCapabilities.STRUCTURE_SUMMARY;
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public boolean supports(PrismLiveInput input) {
        return input instanceof PrismMoleculeLiveInput moleculeInput
                && !moleculeInput.document().idcode().isBlank();
    }

    @Override
    public String fingerprint(PrismLiveInput input, Map<String, Object> configuration) {
        var document = ((PrismMoleculeLiveInput) input).document();
        return document.mode().name() + ":" + document.idcode();
    }

    @Override
    public CompletionStage<PrismLiveResult> compute(
            PrismLiveInput input,
            Map<String, Object> configuration,
            PrismLiveComputationContext context
    ) {
        return context.require(OclLiveCapabilities.DECODED_MOLECULE).thenApply(molecule -> {
            molecule.ensureHelperArrays(StereoMolecule.cHelperRings);
            int heavyAtoms = 0;
            int heteroAtoms = 0;
            int formalCharge = 0;
            for (int atom = 0; atom < molecule.getAtoms(); atom++) {
                int atomicNumber = molecule.getAtomicNo(atom);
                if (atomicNumber != 1) heavyAtoms++;
                if (atomicNumber != 1 && atomicNumber != 6) heteroAtoms++;
                formalCharge += molecule.getAtomCharge(atom);
            }
            RingCollection rings = molecule.getRingSet();
            int aromaticRings = 0;
            for (int ring = 0; ring < rings.getSize(); ring++) {
                if (rings.isAromatic(ring)) aromaticRings++;
            }
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("heavy_atoms", heavyAtoms);
            values.put("hetero_atoms", heteroAtoms);
            values.put("rings", rings.getSize());
            values.put("aromatic_rings", aromaticRings);
            values.put("stereocenters", new PropertyCalculator(molecule).getStereoCenterCount());
            values.put("formal_charge", formalCharge);
            return new PrismLiveResult(
                    "chemistry.ocl.structure_summary.v1",
                    values,
                    List.of(),
                    Map.of("fields", List.of(
                            field("heavy_atoms", "Heavy atoms"),
                            field("hetero_atoms", "Hetero atoms"),
                            field("rings", "Rings"),
                            field("aromatic_rings", "Aromatic rings"),
                            field("stereocenters", "Stereocenters"),
                            field("formal_charge", "Formal charge")
                    ))
            );
        });
    }

    private static Map<String, Object> field(String key, String label) {
        return Map.of("key", key, "label", label, "unit", "");
    }
}
