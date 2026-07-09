package tech.molecules.structurized.prismlite.swing;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public final class PrismLiteSwingExtensions {
    private PrismLiteSwingExtensions() {
    }

    public static List<PrismLiteSwingExtension> load() {
        ArrayList<PrismLiteSwingExtension> extensions = new ArrayList<>();
        ServiceLoader.load(PrismLiteSwingExtension.class).forEach(extensions::add);
        return List.copyOf(extensions);
    }
}
