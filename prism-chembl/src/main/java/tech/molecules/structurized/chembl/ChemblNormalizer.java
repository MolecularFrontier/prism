package tech.molecules.structurized.chembl;

import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.MoleculeStandardizer;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public final class ChemblNormalizer {
    public NormalizedMolecule normalize(String smiles, ChemblFilterOptions options) {
        if (smiles == null || smiles.isBlank()) throw new ChemblNormalizationException(ChemblRejection.NO_USABLE_STRUCTURE, "SMILES is blank");
        try {
            StereoMolecule input = new StereoMolecule();
            new SmilesParser().parse(input, smiles.getBytes(StandardCharsets.UTF_8));
            input.ensureHelperArrays(StereoMolecule.cHelperRings);
            int[] fragmentNumbers = new int[input.getAllAtoms()];
            int fragmentCount = input.getFragmentNumbers(fragmentNumbers, false, false);
            int selectedFragment = selectLargestOrganicFragment(input, fragmentNumbers, fragmentCount);
            boolean[] include = new boolean[input.getAllAtoms()];
            for (int atom = 0; atom < input.getAllAtoms(); atom++) include[atom] = fragmentNumbers[atom] == selectedFragment;
            StereoMolecule molecule = new StereoMolecule();
            input.copyMoleculeByAtoms(molecule, include, true, null);
            molecule.removeExplicitHydrogens(true);
            MoleculeStandardizer.standardize(molecule, MoleculeStandardizer.MODE_REMOVE_ISOTOPS);
            molecule.ensureHelperArrays(StereoMolecule.cHelperCIP);
            validate(molecule, options);
            return new NormalizedMolecule(molecule, fragmentCount, smiles);
        } catch (ChemblNormalizationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ChemblNormalizationException(ChemblRejection.PARSE_FAILURE, exception.getMessage(), exception);
        }
    }

    private static int selectLargestOrganicFragment(StereoMolecule molecule, int[] fragmentNumbers, int fragmentCount) {
        int[] heavyCounts = new int[fragmentCount];
        boolean[] organic = new boolean[fragmentCount];
        for (int atom = 0; atom < molecule.getAllAtoms(); atom++) {
            int fragment = fragmentNumbers[atom];
            if (molecule.getAtomicNo(atom) != 1) heavyCounts[fragment]++;
            organic[fragment] |= molecule.isOrganicAtom(atom);
        }
        int largest = -1;
        int largestSize = -1;
        for (int fragment = 0; fragment < fragmentCount; fragment++) {
            if (!organic[fragment]) continue;
            if (heavyCounts[fragment] > largestSize) {
                largest = fragment;
                largestSize = heavyCounts[fragment];
            }
        }
        if (largest < 0) throw new ChemblNormalizationException(ChemblRejection.NO_USABLE_STRUCTURE, "no organic component");
        for (int fragment = 0; fragment < fragmentCount; fragment++) {
            if (fragment != largest && organic[fragment] && heavyCounts[fragment] == largestSize)
                throw new ChemblNormalizationException(ChemblRejection.MULTIPLE_ORGANIC_COMPONENTS, "equally large organic components");
        }
        return largest;
    }

    public static void validate(StereoMolecule molecule, ChemblFilterOptions options) {
        Set<Integer> allowed = new HashSet<>(options.allowedElements());
        for (int atom = 0; atom < molecule.getAtoms(); atom++) {
            int atomicNo = molecule.getAtomicNo(atom);
            if (!allowed.contains(atomicNo)) throw new ChemblNormalizationException(ChemblRejection.DISALLOWED_ELEMENT, "element " + atomicNo);
            if (molecule.getAtomRadical(atom) != Molecule.cAtomRadicalStateNone)
                throw new ChemblNormalizationException(ChemblRejection.RADICAL, "radical atom");
        }
        molecule.ensureHelperArrays(StereoMolecule.cHelperNeighbours);
        int heavyAtoms = 0;
        int charge = 0;
        for (int atom = 0; atom < molecule.getAtoms(); atom++) {
            if (molecule.getAtomicNo(atom) != 1) heavyAtoms++;
            charge += molecule.getAtomCharge(atom);
            if (molecule.getOccupiedValence(atom) > molecule.getMaxValenceUncharged(atom) + Math.abs(molecule.getAtomCharge(atom)))
                throw new ChemblNormalizationException(ChemblRejection.INVALID_VALENCE, "invalid valence");
        }
        if (heavyAtoms < options.minHeavyAtoms() || heavyAtoms > options.maxHeavyAtoms())
            throw new ChemblNormalizationException(ChemblRejection.HEAVY_ATOM_LIMIT, "heavy atoms: " + heavyAtoms);
        if (charge < options.minCharge() || charge > options.maxCharge())
            throw new ChemblNormalizationException(ChemblRejection.CHARGE_LIMIT, "formal charge: " + charge);
    }
}
