package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.PrismSession;
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
