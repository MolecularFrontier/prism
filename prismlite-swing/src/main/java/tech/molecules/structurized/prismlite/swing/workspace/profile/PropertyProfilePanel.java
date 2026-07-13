package tech.molecules.structurized.prismlite.swing.workspace.profile;

import tech.molecules.structurized.prism.engine.MaterializePropertyProfileOperation;
import tech.molecules.structurized.prism.engine.PrismOperationResult;
import tech.molecules.structurized.prism.engine.PropertyProfileEvaluator;
import tech.molecules.structurized.prism.engine.PropertyProfileRowEvaluation;
import tech.molecules.structurized.prism.score.EndpointScoreEvaluation;
import tech.molecules.structurized.prism.score.MpoEvaluation;
import tech.molecules.structurized.prism.score.MpoStatus;
import tech.molecules.structurized.prism.score.PropertyProfileDefinition;
import tech.molecules.structurized.prism.score.PropertyProfileItem;
import tech.molecules.structurized.prismlite.swing.workspace.PrismLiteWorkspaceModel;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PropertyProfilePanel extends JPanel {
    private final PrismLiteWorkspaceModel model;
    private final Runnable structureChanged;
    private final JComboBox<ProfileChoice> profiles = new JComboBox<>();
    private final JPanel content = new JPanel();
    private final JLabel status = new JLabel(" ");
    private boolean refreshing;

    public PropertyProfilePanel(PrismLiteWorkspaceModel model, Runnable structureChanged) {
        super(new BorderLayout(4, 4));
        this.model = Objects.requireNonNull(model, "model");
        this.structureChanged = structureChanged == null ? () -> { } : structureChanged;
        setPreferredSize(new Dimension(270, 420));

        JPanel controls = new JPanel(new BorderLayout(4, 4));
        controls.setBorder(BorderFactory.createEmptyBorder(5, 5, 2, 5));
        controls.add(profiles, BorderLayout.CENTER);
        JButton materialize = new JButton("Materialize");
        materialize.setToolTipText("Add the profile scores and MPO results as table columns");
        materialize.addActionListener(event -> materializeSelected());
        controls.add(materialize, BorderLayout.EAST);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(4, 6, 8, 6));
        profiles.addActionListener(event -> {
            if (!refreshing) refreshContent();
        });
        status.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));

        add(controls, BorderLayout.NORTH);
        add(new JScrollPane(content), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        refresh();
    }

    public void refresh() {
        String selectedId = selectedProfile() == null ? null : selectedProfile().id();
        refreshing = true;
        profiles.removeAllItems();
        for (PropertyProfileDefinition profile : model.session().propertyProfiles()) {
            profiles.addItem(new ProfileChoice(profile.id(), profile.title()));
        }
        if (selectedId != null) {
            for (int i = 0; i < profiles.getItemCount(); i++) {
                if (selectedId.equals(profiles.getItemAt(i).id())) profiles.setSelectedIndex(i);
            }
        }
        refreshing = false;
        refreshContent();
    }

    private void refreshContent() {
        content.removeAll();
        PropertyProfileDefinition profile = selectedProfileDefinition();
        Integer row = model.focusedPhysicalRow();
        if (profile == null) {
            content.add(message("No property profiles in this PrismPack."));
            status.setText(" ");
        } else if (row == null) {
            content.add(message("Select a compound to inspect its profile."));
            status.setText(profile.items().size() + " endpoints");
        } else {
            PropertyProfileRowEvaluation evaluation = PropertyProfileEvaluator.evaluate(model.session().snapshot(), profile, row);
            JLabel subject = new JLabel(evaluation.rowId());
            subject.setFont(subject.getFont().deriveFont(Font.BOLD));
            subject.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(subject);
            content.add(Box.createVerticalStrut(6));
            String currentGroup = null;
            for (PropertyProfileItem item : profile.items()) {
                if (!item.visible()) continue;
                String group = item.group() == null ? "Properties" : item.group();
                if (!group.equals(currentGroup)) {
                    content.add(groupHeading(group));
                    currentGroup = group;
                }
                content.add(scoreRow(item, item.scoreId() == null ? null : evaluation.scores().get(item.scoreId())));
            }
            if (!profile.mpos().isEmpty()) {
                content.add(groupHeading("MPO"));
                for (MpoEvaluation mpo : evaluation.mpos().values()) content.add(mpoRow(mpo));
            }
            status.setText(profile.items().size() + " endpoints   " + profile.mpos().size() + " MPO");
        }
        content.revalidate();
        content.repaint();
    }

    private JPanel scoreRow(PropertyProfileItem item, EndpointScoreEvaluation evaluation) {
        JPanel row = new JPanel(new BorderLayout(5, 1));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        JLabel name = new JLabel(item.displayLabel());
        String value = evaluation == null || evaluation.inputValue() == null ? "missing" : String.valueOf(evaluation.inputValue());
        JLabel raw = new JLabel(value, SwingConstants.RIGHT);
        raw.setForeground(new Color(85, 85, 85));
        row.add(name, BorderLayout.WEST);
        row.add(raw, BorderLayout.EAST);
        if (evaluation != null && evaluation.available()) {
            JProgressBar score = scoreBar(evaluation.score());
            score.setToolTipText(item.displayLabel() + ": " + ScoreDisplayService.format(evaluation.score()));
            row.add(score, BorderLayout.SOUTH);
        } else {
            JLabel missing = new JLabel("No score", SwingConstants.CENTER);
            missing.setForeground(new Color(130, 130, 130));
            row.add(missing, BorderLayout.SOUTH);
        }
        return row;
    }

    private JPanel mpoRow(MpoEvaluation mpo) {
        JPanel row = new JPanel(new BorderLayout(5, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        JLabel name = new JLabel(mpo.mpoId());
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        JLabel state = new JLabel(mpo.status().name(), SwingConstants.RIGHT);
        state.setForeground(statusColor(mpo.status()));
        row.add(name, BorderLayout.WEST);
        row.add(state, BorderLayout.EAST);
        if (mpo.score() != null) row.add(scoreBar(mpo.score()), BorderLayout.CENTER);
        JLabel coverage = new JLabel("Coverage " + Math.round(mpo.coverage() * 100.0) + "%", SwingConstants.RIGHT);
        coverage.setForeground(new Color(90, 90, 90));
        row.add(coverage, BorderLayout.SOUTH);
        return row;
    }

    private static JProgressBar scoreBar(double value) {
        JProgressBar bar = new JProgressBar(0, 1000);
        bar.setValue((int) Math.round(Math.max(0.0, Math.min(1.0, value)) * 1000));
        bar.setStringPainted(true);
        bar.setString(ScoreDisplayService.format(value));
        bar.setForeground(ScoreDisplayService.scoreColor(value));
        bar.setPreferredSize(new Dimension(120, 18));
        return bar;
    }

    private static JLabel groupHeading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(8, 0, 3, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel message(String text) {
        JLabel label = new JLabel("<html>" + text + "</html>");
        label.setForeground(new Color(100, 100, 100));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void materializeSelected() {
        PropertyProfileDefinition profile = selectedProfileDefinition();
        if (profile == null) return;
        try {
            PrismOperationResult result = model.session().runOperation(MaterializePropertyProfileOperation.ID,
                    Map.of("profileId", profile.id()));
            status.setText(result.addedColumns().size() + " columns added"
                    + (result.warnings().isEmpty() ? "" : "   " + result.warnings().size() + " warnings"));
            structureChanged.run();
        } catch (RuntimeException exception) {
            status.setText(exception.getMessage());
        }
    }

    private PropertyProfileDefinition selectedProfileDefinition() {
        ProfileChoice selected = selectedProfile();
        if (selected == null) return null;
        return model.session().propertyProfiles().stream()
                .filter(profile -> profile.id().equals(selected.id())).findFirst().orElse(null);
    }

    private ProfileChoice selectedProfile() {
        return profiles.getSelectedItem() instanceof ProfileChoice choice ? choice : null;
    }

    private static Color statusColor(MpoStatus status) {
        return switch (status) {
            case PASS -> new Color(34, 123, 73);
            case WARNING -> new Color(160, 112, 20);
            case FAIL -> new Color(174, 48, 45);
            case INSUFFICIENT_DATA -> new Color(105, 105, 105);
        };
    }

    private record ProfileChoice(String id, String title) {
        @Override
        public String toString() {
            return title;
        }
    }
}
