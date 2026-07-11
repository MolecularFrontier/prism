package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.CachePolicy;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.engine.ocl.OclCreateSubstructureRowSetOperation;
import tech.molecules.structurized.prism.engine.ocl.OclPrismEngineSupport;
import tech.molecules.structurized.prism.engine.ocl.OclStructureFormat;
import tech.molecules.structurized.prismlite.swing.workspace.chem.StructureCoordinateResolver;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspacePanel;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class PrismLiteFrame extends JFrame {
    private final PrismLiteWorkspacePanel workspace;

    public PrismLiteFrame(PrismSession session, Path sourcePath) {
        this(session, sourcePath, PrismLiteSwingExtensions.load());
    }

    PrismLiteFrame(PrismSession session, Path sourcePath, List<PrismLiteSwingExtension> extensions) {
        super(titleFor(sourcePath));
        configureChemistry(session);
        List<PrismLiteSwingExtension> loadedExtensions = List.copyOf(extensions);
        loadedExtensions.forEach(extension -> extension.configureSession(session));
        this.workspace = new PrismLiteWorkspacePanel(session);
        setContentPane(workspace);
        setSize(1240, 760);
        setLocationByPlatform(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loadedExtensions.forEach(extension -> extension.configureSwing(new PrismLiteSwingContext(
                session,
                this,
                workspace.table(),
                workspace.tableModel(),
                workspace.sidePanel(),
                workspace::refreshWorkspace
        )));
        workspace.refreshWorkspace();
    }

    private static void configureChemistry(PrismSession session) {
        for (PrismColumn column : session.baseTable().columns()) {
            if (column.type() != PrismColumnType.MOLECULE) {
                continue;
            }
            try {
                String coordinatesColumnId = StructureCoordinateResolver.coordinateColumnId(session.baseTable(), column);
                OclPrismEngineSupport.registerStructureColumn(
                        session,
                        column.id(),
                        OclStructureFormat.fromMetadata(column.schema().structureFormat()),
                        coordinatesColumnId,
                        CachePolicy.LAZY);
            } catch (IllegalArgumentException ignored) {
                // A plugin or caller may already have registered the same OCL computed values.
            }
        }
        try {
            if (!session.operationRegistry().operationIds().contains(OclCreateSubstructureRowSetOperation.ID)) {
                session.operationRegistry().register(new OclCreateSubstructureRowSetOperation());
            }
        } catch (IllegalArgumentException ignored) {
            // A plugin or caller may already have registered the same OCL operation.
        }
    }

    private static String titleFor(Path sourcePath) {
        if (sourcePath == null) {
            return "PrismLite";
        }
        Path fileName = sourcePath.getFileName();
        return "PrismLite - " + (fileName == null ? sourcePath : fileName);
    }

    public static void show(PrismSession session, Path sourcePath) {
        open(session, sourcePath);
    }

    public static PrismLiteFrame open(PrismSession session, Path sourcePath) {
        if (SwingUtilities.isEventDispatchThread()) {
            PrismLiteFrame frame = new PrismLiteFrame(session, sourcePath);
            frame.setVisible(true);
            return frame;
        }
        AtomicReference<PrismLiteFrame> frame = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                PrismLiteFrame created = new PrismLiteFrame(session, sourcePath);
                created.setVisible(true);
                frame.set(created);
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while opening PrismLite frame", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Failed to open PrismLite frame", cause);
        }
        return frame.get();
    }
}
