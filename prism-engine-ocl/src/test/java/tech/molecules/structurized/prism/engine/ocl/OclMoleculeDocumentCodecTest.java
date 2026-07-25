package tech.molecules.structurized.prism.engine.ocl;

import org.junit.jupiter.api.Test;
import tech.molecules.structurized.prism.engine.PrismMoleculeDocument;
import tech.molecules.structurized.prism.engine.PrismMoleculeDocumentMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OclMoleculeDocumentCodecTest {
    private final OclMoleculeDocumentCodec codec = new OclMoleculeDocumentCodec();

    @Test
    void roundTripsMoleculeWithCoordinates() {
        OclMoleculeDocumentCodec.EncodedMolecule encoded = codec.parse("CC(=O)N", PrismMoleculeDocumentMode.MOLECULE);
        PrismMoleculeDocument document = new PrismMoleculeDocument(
                "m1", "Acetamide", PrismMoleculeDocumentMode.MOLECULE,
                encoded.idcode(), encoded.coordinates(), 1
        );

        assertFalse(document.coordinates().isBlank());
        assertEquals("CC(N)=O", codec.interchange(document));
        assertFalse(codec.decode(document).isFragment());
    }

    @Test
    void preservesFragmentQueryMode() {
        OclMoleculeDocumentCodec.EncodedMolecule encoded = codec.parse("[c,n]1ccccc1[*]", PrismMoleculeDocumentMode.FRAGMENT);
        PrismMoleculeDocument document = new PrismMoleculeDocument(
                "q1", "Aryl exit vector", PrismMoleculeDocumentMode.FRAGMENT,
                encoded.idcode(), encoded.coordinates(), 1
        );

        assertTrue(codec.decode(document).isFragment());
        assertFalse(codec.interchange(document).isBlank());
    }

    @Test
    void supportsBlankEditorDocuments() {
        PrismMoleculeDocument document = new PrismMoleculeDocument(
                "empty", "Empty", PrismMoleculeDocumentMode.MOLECULE, "", "", 1
        );

        assertEquals(0, codec.decode(document).getAllAtoms());
    }
}
