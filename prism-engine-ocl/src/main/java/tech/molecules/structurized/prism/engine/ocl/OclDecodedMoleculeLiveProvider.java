package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.StereoMolecule;
import tech.molecules.structurized.prism.engine.live.PrismLiveCapability;
import tech.molecules.structurized.prism.engine.live.PrismLiveComputationContext;
import tech.molecules.structurized.prism.engine.live.PrismLiveComputationProvider;
import tech.molecules.structurized.prism.engine.live.PrismLiveInput;
import tech.molecules.structurized.prism.engine.live.PrismMoleculeLiveInput;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class OclDecodedMoleculeLiveProvider implements PrismLiveComputationProvider<StereoMolecule> {
    private final OclMoleculeDocumentCodec codec = new OclMoleculeDocumentCodec();

    @Override
    public PrismLiveCapability<StereoMolecule> capability() {
        return OclLiveCapabilities.DECODED_MOLECULE;
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
    public CompletionStage<StereoMolecule> compute(
            PrismLiveInput input,
            Map<String, Object> configuration,
            PrismLiveComputationContext context
    ) {
        var document = ((PrismMoleculeLiveInput) input).document();
        return CompletableFuture.completedFuture(codec.decode(document));
    }
}
