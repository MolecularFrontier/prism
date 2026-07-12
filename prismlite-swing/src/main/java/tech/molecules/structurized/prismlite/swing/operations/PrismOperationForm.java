package tech.molecules.structurized.prismlite.swing.operations;

import javax.swing.JComponent;
import java.util.Map;

public interface PrismOperationForm {
    JComponent component();

    Map<String, Object> collectParameters();
}
