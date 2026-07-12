package tech.molecules.structurized.prismlite.swing;

import tech.molecules.structurized.prism.engine.CachePolicy;
import tech.molecules.structurized.prism.engine.CreateScatterPlotViewOperation;
import tech.molecules.structurized.prism.engine.PrismColumn;
import tech.molecules.structurized.prism.engine.PrismColumnType;
import tech.molecules.structurized.prism.engine.PrismSession;
import tech.molecules.structurized.prism.io.PrismTsvDatasetLoader;
import tech.molecules.structurized.prism.provider.inmemory.InMemoryPrismDataset;
import tech.molecules.structurized.prism.engine.ocl.OclCreateStructureGridViewOperation;
import tech.molecules.structurized.prism.engine.ocl.OclCreateSubstructureRowSetOperation;
import tech.molecules.structurized.prism.engine.ocl.OclPrismEngineSupport;
import tech.molecules.structurized.prism.engine.ocl.OclStructureFormat;
import tech.molecules.structurized.prismlite.swing.chembl.ChemblPublicationClient;
import tech.molecules.structurized.prismlite.swing.chembl.ChemblPublicationImportDialog;
import tech.molecules.structurized.prismlite.swing.chembl.ChemblPublicationImportOptions;
import tech.molecules.structurized.prismlite.swing.chembl.ChemblPublicationImporter;
import tech.molecules.structurized.prismlite.swing.workspace.chem.StructureCoordinateResolver;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspacePanel;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.ExecutionException;
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
        setJMenuBar(menuBar());
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


    private JMenuBar menuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu());
        menuBar.add(viewMenu());
        menuBar.add(dataMenu());
        menuBar.add(helpMenu());
        return menuBar;
    }

    private JMenu fileMenu() {
        JMenu menu = new JMenu("File");
        JMenuItem openPack = new JMenuItem("Open PrismPack...");
        openPack.addActionListener(event -> openPrismPack());
        JMenuItem importDataset = new JMenuItem("Import PRISM TSV Dataset...");
        importDataset.addActionListener(event -> importPrismTsvDataset());
        JMenuItem importChembl = new JMenuItem("Import ChEMBL Publication...");
        importChembl.addActionListener(event -> importChemblPublication());
        JMenuItem close = new JMenuItem("Close");
        close.addActionListener(event -> dispose());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(event -> System.exit(0));
        menu.add(openPack);
        menu.add(importDataset);
        menu.add(importChembl);
        menu.addSeparator();
        menu.add(close);
        menu.add(exit);
        return menu;
    }

    private JMenu viewMenu() {
        JMenu menu = new JMenu("View");
        JMenuItem columns = new JMenuItem("Toggle Column Navigator");
        columns.addActionListener(event -> workspace.toggleNavigator());
        JMenuItem inspector = new JMenuItem("Toggle Inspector");
        inspector.addActionListener(event -> workspace.toggleInspector());
        JMenuItem autoWidth = new JMenuItem("Auto Column Width");
        autoWidth.addActionListener(event -> workspace.autoSizeVisibleColumns());
        JMenuItem resetRowHeight = new JMenuItem("Reset Row Height");
        resetRowHeight.addActionListener(event -> workspace.resetRowHeight());
        menu.add(columns);
        menu.add(inspector);
        menu.addSeparator();
        menu.add(autoWidth);
        menu.add(resetRowHeight);
        return menu;
    }

    private JMenu dataMenu() {
        JMenu menu = new JMenu("Data");
        JMenuItem applyAll = new JMenuItem("Apply All Draft Filters");
        applyAll.addActionListener(event -> workspace.applyAllDraftFilters());
        JMenuItem discardAll = new JMenuItem("Discard Draft Filters");
        discardAll.addActionListener(event -> workspace.discardAllDraftFilters());
        JMenuItem clearFilters = new JMenuItem("Clear Applied Filters");
        clearFilters.addActionListener(event -> workspace.clearAppliedFilters());
        menu.add(applyAll);
        menu.add(discardAll);
        menu.addSeparator();
        menu.add(clearFilters);
        return menu;
    }

    private JMenu helpMenu() {
        JMenu menu = new JMenu("Help");
        JMenuItem about = new JMenuItem("About PrismLite");
        about.addActionListener(event -> JOptionPane.showMessageDialog(
                this,
                "PrismLite\nA compact PrismPack workspace",
                "About PrismLite",
                JOptionPane.INFORMATION_MESSAGE
        ));
        menu.add(about);
        return menu;
    }

    private void openPrismPack() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new FileNameExtensionFilter("PrismPack (*.prismpack)", "prismpack"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            replaceSession(PrismSession.open(path), path);
        } catch (IOException | RuntimeException exception) {
            showLoadError("Open PrismPack", exception);
        }
    }

    private void importPrismTsvDataset() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path directory = chooser.getSelectedFile().toPath();
        try {
            InMemoryPrismDataset dataset = PrismTsvDatasetLoader.load(directory);
            PrismLiteDatasetImporter.chooseImport(this, dataset)
                    .map(selection -> PrismLiteDatasetImporter.toSession(dataset, selection, directory))
                    .ifPresent(session -> replaceSession(session, directory));
        } catch (IOException | RuntimeException exception) {
            showLoadError("Import PRISM TSV Dataset", exception);
        }
    }

    private void importChemblPublication() {
        Optional<ChemblPublicationImportOptions> selectedOptions = ChemblPublicationImportDialog.prompt(this);
        if (selectedOptions.isEmpty()) {
            return;
        }
        ChemblPublicationImportOptions options = selectedOptions.get();
        JDialog progress = progressDialog("Import ChEMBL Publication", "Fetching " + options.documentChemblId() + " from ChEMBL...");
        SwingWorker<InMemoryPrismDataset, Void> worker = new SwingWorker<>() {
            @Override
            protected InMemoryPrismDataset doInBackground() throws Exception {
                ChemblPublicationClient client = new ChemblPublicationClient();
                return new ChemblPublicationImporter().importPublication(client.fetchPublication(options.documentChemblId()), options);
            }

            @Override
            protected void done() {
                progress.dispose();
                try {
                    InMemoryPrismDataset dataset = get();
                    Path sourcePath = Path.of("chembl-publication-" + options.documentChemblId().toLowerCase());
                    PrismLiteDatasetImporter.chooseImport(PrismLiteFrame.this, dataset, "Import ChEMBL Publication")
                            .map(selection -> PrismLiteDatasetImporter.toSession(dataset, selection, sourcePath))
                            .ifPresent(session -> replaceSession(session, sourcePath));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showLoadError("Import ChEMBL Publication", exception);
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    showLoadError("Import ChEMBL Publication", cause instanceof Exception ex ? ex : new RuntimeException(cause));
                } catch (RuntimeException exception) {
                    showLoadError("Import ChEMBL Publication", exception);
                }
            }
        };
        worker.execute();
        progress.setVisible(true);
    }

    private JDialog progressDialog(String title, String message) {
        JDialog dialog = new JDialog(this, title, true);
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.add(new javax.swing.JLabel(message), BorderLayout.NORTH);
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        panel.add(progress, BorderLayout.CENTER);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        return dialog;
    }

    private void replaceSession(PrismSession session, Path sourcePath) {
        PrismLiteFrame frame = new PrismLiteFrame(session, sourcePath);
        frame.setSize(getSize());
        frame.setLocation(getLocation());
        frame.setExtendedState(getExtendedState());
        frame.setVisible(true);
        dispose();
    }

    private void showLoadError(String title, Exception exception) {
        JOptionPane.showMessageDialog(
                this,
                exception.getMessage() == null ? exception.toString() : exception.getMessage(),
                title + " failed",
                JOptionPane.ERROR_MESSAGE
        );
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
            if (!session.operationRegistry().operationIds().contains(OclCreateStructureGridViewOperation.ID)) {
                session.operationRegistry().register(new OclCreateStructureGridViewOperation());
            }
            if (!session.operationRegistry().operationIds().contains(CreateScatterPlotViewOperation.ID)) {
                session.operationRegistry().register(new CreateScatterPlotViewOperation());
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
