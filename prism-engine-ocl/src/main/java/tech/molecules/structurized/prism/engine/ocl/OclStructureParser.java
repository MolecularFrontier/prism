package tech.molecules.structurized.prism.engine.ocl;

import com.actelion.research.chem.IDCodeParser;
import com.actelion.research.chem.MolfileParser;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;

import java.nio.charset.StandardCharsets;

public final class OclStructureParser {
    public StereoMolecule parse(String structureText, OclStructureFormat format) {
        return parse(structureText, null, format);
    }

    public StereoMolecule parse(String structureText, String coordinatesText, OclStructureFormat format) {
        if (structureText == null || structureText.isBlank()) {
            return null;
        }
        OclStructureFormat effectiveFormat = format == null ? OclStructureFormat.SMILES : format;
        try {
            StereoMolecule molecule = switch (effectiveFormat) {
                case IDCODE -> parseIdcode(structureText, coordinatesText);
                case SMILES -> parseSmiles(structureText);
                case MOLFILE -> parseMolfile(structureText);
            };
            if (molecule == null || molecule.getAllAtoms() == 0) {
                return null;
            }
            molecule.ensureHelperArrays(StereoMolecule.cHelperCIP);
            return molecule;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Failed to parse " + effectiveFormat + " structure", exception);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to parse " + effectiveFormat + " structure", exception);
        }
    }

    private static StereoMolecule parseIdcode(String idcode, String coordinates) {
        IDCodeParser parser = new IDCodeParser(true);
        if (coordinates == null || coordinates.isBlank()) {
            return parser.getCompactMolecule(idcode);
        }
        return parser.getCompactMolecule(idcode, coordinates);
    }

    private static StereoMolecule parseSmiles(String smiles) throws Exception {
        StereoMolecule molecule = new StereoMolecule();
        new SmilesParser().parse(molecule, smiles.getBytes(StandardCharsets.UTF_8));
        return molecule;
    }

    private static StereoMolecule parseMolfile(String molfile) {
        StereoMolecule molecule = new StereoMolecule();
        boolean ok = new MolfileParser().parse(molecule, molfile);
        if (!ok) {
            throw new IllegalArgumentException("invalid molfile");
        }
        return molecule;
    }
}
