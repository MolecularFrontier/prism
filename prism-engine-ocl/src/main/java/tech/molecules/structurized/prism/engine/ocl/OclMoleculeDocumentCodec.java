package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.IDCodeParser;
import com.actelion.research.chem.IsomericSmilesCreator;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.coords.CoordinateInventor;
import tech.molecules.structurized.prism.engine.PrismMoleculeDocument;
import tech.molecules.structurized.prism.engine.PrismMoleculeDocumentMode;

import java.nio.charset.StandardCharsets;

public final class OclMoleculeDocumentCodec {
    public EncodedMolecule parse(String text, PrismMoleculeDocumentMode mode) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("molecule input must not be blank");
        }
        PrismMoleculeDocumentMode effectiveMode = mode == null ? PrismMoleculeDocumentMode.MOLECULE : mode;
        try {
            StereoMolecule molecule = new StereoMolecule();
            SmilesParser parser = effectiveMode == PrismMoleculeDocumentMode.FRAGMENT
                    ? new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMARTS)
                    : new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMILES);
            parser.parse(molecule, text.getBytes(StandardCharsets.UTF_8));
            molecule.setFragment(effectiveMode == PrismMoleculeDocumentMode.FRAGMENT);
            if (molecule.getAllAtoms() == 0) {
                throw new IllegalArgumentException("molecule input contains no atoms");
            }
            new CoordinateInventor().invent(molecule);
            return encode(molecule, effectiveMode);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            String format = effectiveMode == PrismMoleculeDocumentMode.FRAGMENT ? "SMARTS" : "SMILES";
            throw new IllegalArgumentException("invalid " + format + " molecule input", exception);
        }
    }

    public EncodedMolecule encode(StereoMolecule source, PrismMoleculeDocumentMode mode) {
        if (source == null) throw new IllegalArgumentException("molecule must not be null");
        PrismMoleculeDocumentMode effectiveMode = mode == null ? PrismMoleculeDocumentMode.MOLECULE : mode;
        StereoMolecule molecule = new StereoMolecule(source);
        molecule.setFragment(effectiveMode == PrismMoleculeDocumentMode.FRAGMENT);
        if (molecule.getAllAtoms() == 0) return new EncodedMolecule("", "");
        molecule.ensureHelperArrays(StereoMolecule.cHelperCIP);
        Canonizer canonizer = new Canonizer(molecule);
        return new EncodedMolecule(canonizer.getIDCode(), canonizer.getEncodedCoordinates());
    }

    public StereoMolecule decode(PrismMoleculeDocument document) {
        if (document == null) throw new IllegalArgumentException("molecule document must not be null");
        if (document.idcode().isBlank()) {
            StereoMolecule empty = new StereoMolecule();
            empty.setFragment(document.mode() == PrismMoleculeDocumentMode.FRAGMENT);
            return empty;
        }
        IDCodeParser parser = new IDCodeParser(true);
        StereoMolecule molecule = document.coordinates().isBlank()
                ? parser.getCompactMolecule(document.idcode())
                : parser.getCompactMolecule(document.idcode(), document.coordinates());
        molecule.setFragment(document.mode() == PrismMoleculeDocumentMode.FRAGMENT);
        molecule.ensureHelperArrays(StereoMolecule.cHelperCIP);
        return molecule;
    }

    public String interchange(PrismMoleculeDocument document) {
        StereoMolecule molecule = decode(document);
        if (molecule.getAllAtoms() == 0) return "";
        return document.mode() == PrismMoleculeDocumentMode.FRAGMENT
                ? IsomericSmilesCreator.createSmarts(molecule)
                : IsomericSmilesCreator.createSmiles(molecule);
    }

    public record EncodedMolecule(String idcode, String coordinates) {
        public EncodedMolecule {
            idcode = idcode == null ? "" : idcode.trim();
            coordinates = coordinates == null ? "" : coordinates.trim();
        }
    }
}
