package tech.molecules.structurized.chembl;

import java.io.Closeable;
import java.io.IOException;

public interface ChemblSource extends Closeable {
    boolean hasNext() throws IOException;
    ChemblRecord next() throws IOException;

    @Override
    default void close() throws IOException {
    }
}
