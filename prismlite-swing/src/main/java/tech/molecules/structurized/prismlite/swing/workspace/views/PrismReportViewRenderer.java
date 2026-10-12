package tech.molecules.structurized.prismlite.swing.workspace.views;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import tech.molecules.structurized.prism.engine.PrismViewRecord;
import tech.molecules.structurized.prism.report.EmbeddedPrismViewReportBlock;
import tech.molecules.structurized.prism.report.MarkdownReportBlock;
import tech.molecules.structurized.prism.report.PrismReportBlock;
import tech.molecules.structurized.prism.report.PrismReportSeverity;
import tech.molecules.structurized.prism.report.PrismReportViewSpec;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceController;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PrismReportViewRenderer implements PrismSwingViewRenderer {
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();
    private final PrismSwingViewRendererRegistry embeddedRenderers;

    public PrismReportViewRenderer() {
        this(PrismSwingViewRendererRegistry.embeddedDefaults());
    }

    public PrismReportViewRenderer(PrismSwingViewRendererRegistry embeddedRenderers) {
        this.embeddedRenderers = embeddedRenderers;
    }

    @Override
    public String viewType() {
        return PrismReportViewSpec.VIEW_TYPE;
    }

    @Override
    public JComponent createComponent(
            PrismViewRecord view,
            PrismLiteWorkspaceModel model,
            PrismLiteWorkspaceController controller,
            Runnable refresh
    ) {
        if (!(view.specification() instanceof PrismReportViewSpec specification)) {
            return message("Unsupported Prism report specification.");
        }
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(14, 18, 20, 18));
        content.add(reportHeader(view, specification));
        content.add(Box.createVerticalStrut(8));
        for (PrismReportBlock block : specification.document().blocks()) {
            JComponent component;
            if (block instanceof MarkdownReportBlock markdown) {
                component = markdown(markdown.markdown());
            } else if (block instanceof EmbeddedPrismViewReportBlock embedded) {
                component = embeddedView(view, embedded, model, controller, refresh);
            } else {
                component = message("Unsupported report block.");
            }
            component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            content.add(component);
            content.add(Box.createVerticalStrut(12));
        }
        JScrollPane scroll = new JScrollPane(content);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setBorder(null);
        return scroll;
    }

    private JComponent reportHeader(PrismViewRecord view, PrismReportViewSpec specification) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("<html><h1>" + escape(specification.title()) + "</h1></html>");
        title.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        panel.add(title);
        String dataset = String.valueOf(view.provenance().getOrDefault("datasetSource", "current dataset"));
        String origin = String.valueOf(view.provenance().getOrDefault("reportSource", "runtime report"));
        String created = specification.document().metadata().createdAt();
        JLabel binding = new JLabel("<html><b>Dataset:</b> " + escape(dataset)
                + " &nbsp; <b>Revision:</b> unavailable"
                + "<br><b>Report:</b> " + escape(origin)
                + (created == null ? "" : " &nbsp; <b>Created:</b> " + escape(created))
                + "</html>");
        binding.setForeground(new Color(75, 80, 88));
        binding.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        panel.add(binding);
        for (var diagnostic : specification.document().diagnostics()) {
            if (diagnostic.severity() == PrismReportSeverity.WARNING) {
                JLabel warning = new JLabel("Warning: " + diagnostic.displayMessage());
                warning.setForeground(new Color(150, 92, 0));
                warning.setAlignmentX(JComponent.LEFT_ALIGNMENT);
                panel.add(warning);
            }
        }
        return panel;
    }

    private JComponent markdown(String markdown) {
        Node document = markdownParser.parse(markdown);
        JEditorPane pane = new JEditorPane("text/html", htmlRenderer.render(document));
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        int lines = Math.max(2, markdown.split("\\R", -1).length);
        pane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(60, Math.min(500, lines * 22))));
        pane.setPreferredSize(new Dimension(700, Math.max(60, Math.min(500, lines * 22))));
        return pane;
    }

    private JComponent embeddedView(
            PrismViewRecord reportView,
            EmbeddedPrismViewReportBlock block,
            PrismLiteWorkspaceModel model,
            PrismLiteWorkspaceController controller,
            Runnable refresh
    ) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 218, 223)),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.add(new JLabel(block.blockId()));
        toolbar.add(Box.createHorizontalGlue());
        JButton open = new JButton("Open as full view");
        open.addActionListener(event -> {
            String viewId = uniqueViewId(model, reportView.id() + ":" + block.blockId());
            var full = block.specification().copyWithIdentity(viewId, block.specification().title());
            Map<String, Object> provenance = new LinkedHashMap<>(reportView.provenance());
            provenance.put("sourceReportViewId", reportView.id());
            provenance.put("sourceReportBlockId", block.blockId());
            provenance.put("openedAt", Instant.now().toString());
            PrismViewRecord opened = new PrismViewRecord(
                    full.viewId(), full.viewType(), full.title(), full, Instant.now(), provenance);
            if (controller == null) {
                model.session().addView(opened);
                refresh.run();
            } else {
                controller.addAndFocusView(opened);
            }
        });
        toolbar.add(open);
        wrapper.add(toolbar, BorderLayout.NORTH);
        PrismSwingViewRenderer renderer = embeddedRenderers.find(block.specification().viewType()).orElse(null);
        if (renderer == null) {
            wrapper.add(message("No Swing renderer is registered for " + block.specification().viewType() + "."),
                    BorderLayout.CENTER);
        } else {
            PrismViewRecord embedded = new PrismViewRecord(
                    block.specification().viewId(), block.specification().viewType(),
                    block.specification().title(), block.specification(),
                    reportView.createdAt(), reportView.provenance());
            wrapper.add(renderer.createComponent(embedded, model, controller, refresh), BorderLayout.CENTER);
        }
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 520));
        return wrapper;
    }

    private static String uniqueViewId(PrismLiteWorkspaceModel model, String base) {
        String candidate = base;
        int suffix = 2;
        while (containsView(model, candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static boolean containsView(PrismLiteWorkspaceModel model, String viewId) {
        return model.session().views().stream().anyMatch(view -> view.id().equals(viewId));
    }

    private static JPanel message(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(text, SwingConstants.CENTER), BorderLayout.CENTER);
        return panel;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
