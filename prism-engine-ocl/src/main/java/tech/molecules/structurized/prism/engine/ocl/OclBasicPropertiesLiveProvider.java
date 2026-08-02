package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.MolecularFormula;
import com.actelion.research.chem.prediction.PropertyCalculator;
import tech.molecules.structurized.prism.engine.PrismMoleculeDocumentMode;
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

final class OclBasicPropertiesLiveProvider implements PrismLiveComputationProvider<PrismLiveResult> {
    @Override
    public PrismLiveCapability<PrismLiveResult> capability() {
        return OclLiveCapabilities.BASIC_PROPERTIES;
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public boolean supports(PrismLiveInput input) {
        return input instanceof PrismMoleculeLiveInput moleculeInput
                && moleculeInput.document().mode() == PrismMoleculeDocumentMode.MOLECULE
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
            PropertyCalculator calculator = new PropertyCalculator(molecule);
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("molecular_weight", round(new MolecularFormula(molecule).getRelativeWeight(), 3));
            values.put("clogp", round(calculator.getLogP(), 3));
            values.put("tpsa", round(calculator.getPolarSurfaceArea(), 2));
            values.put("h_bond_donors", calculator.getDonorCount());
            values.put("h_bond_acceptors", calculator.getAcceptorCount());
            values.put("rotatable_bonds", calculator.getRotatableBondCount());
            return new PrismLiveResult(
                    "chemistry.ocl.basic_properties.v1",
                    values,
                    List.of(),
                    Map.of("fields", List.of(
                            field("molecular_weight", "Molecular weight", "g/mol"),
                            field("clogp", "cLogP", ""),
                            field("tpsa", "Topological polar surface area", "A2"),
                            field("h_bond_donors", "H-bond donors", ""),
                            field("h_bond_acceptors", "H-bond acceptors", ""),
                            field("rotatable_bonds", "Rotatable bonds", "")
                    ))
            );
        });
    }

    private static Map<String, Object> field(String key, String label, String unit) {
        return Map.of("key", key, "label", label, "unit", unit);
    }

    private static double round(double value, int digits) {
        double factor = Math.pow(10, digits);
        return Math.round(value * factor) / factor;
    }
}
