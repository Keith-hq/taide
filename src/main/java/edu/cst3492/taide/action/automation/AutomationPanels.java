package edu.cst3492.taide.action.automation;

import com.intellij.icons.AllIcons;
import edu.cst3492.taide.rag.RetrievedChunk;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.function.Consumer;

final class AutomationPanels {
    private AutomationPanels() {}

    static JTextArea text(String content, boolean editable, boolean code) {
        var area = new JTextArea(content);
        area.setEditable(editable);
        area.setLineWrap(!code);
        area.setWrapStyleWord(true);
        area.setTabSize(4);
        area.setFont(new Font(code ? Font.MONOSPACED : Font.SANS_SERIF, Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        area.setCaretPosition(0);
        return area;
    }

    static JComponent review(CourseReview review, Consumer<RetrievedChunk> openSource, Consumer<String> copy) {
        var panel = new JPanel(new BorderLayout(0, 8));
        panel.setPreferredSize(new Dimension(780, 540));
        var header = new JPanel(new BorderLayout(8, 0));
        String notice = review.grounded() ? "Course-based review"
                : CourseReviewGenerator.GENERAL_KNOWLEDGE_NOTICE;
        JTextArea banner = text(notice, false, false);
        banner.setOpaque(false);
        banner.setRows(review.grounded() ? 1 : 2);
        header.add(banner, BorderLayout.CENTER);
        var copyButton = new JButton(AllIcons.Actions.Copy);
        copyButton.setMargin(new Insets(3, 3, 3, 3));
        copyButton.setPreferredSize(new Dimension(30, 30));
        copyButton.setToolTipText("Copy review");
        copyButton.getAccessibleContext().setAccessibleName("Copy review");
        copyButton.addActionListener(event -> copy.accept(formatReview(review)));
        var toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        toolbar.add(copyButton);
        header.add(toolbar, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        var tableModel = new DefaultTableModel(new String[]{"Severity", "Finding", "Sources"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        review.findings().forEach(finding -> tableModel.addRow(
                new Object[]{finding.severity(), finding.title(), finding.sources().size()}));
        var table = new JTable(tableModel);
        var plainTextRenderer = new DefaultTableCellRenderer();
        plainTextRenderer.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, plainTextRenderer);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setMaxWidth(95);
        table.getColumnModel().getColumn(2).setMaxWidth(65);
        var summary = new JPanel(new BorderLayout());
        var tableScroll = new JScrollPane(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        summary.add(tableScroll, BorderLayout.CENTER);
        if (review.findings().isEmpty()) {
            summary.add(new JLabel(review.grounded() ? "No course-supported issues found."
                    : "No issues found in the selected code."), BorderLayout.SOUTH);
        }

        var detail = text("", false, false);
        var citations = new DefaultListModel<RetrievedChunk>();
        var sourceList = new JList<>(citations);
        sourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sourceList.setVisibleRowCount(3);
        sourceList.setCellRenderer((list, source, index, selected, focused) -> {
            var label = new JLabel("[p. " + source.slideNumber() + "] " + source.documentName());
            label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            label.setToolTipText("Source: " + source.documentName() + ", page " + source.slideNumber());
            return label;
        });
        var open = new JButton(AllIcons.Actions.MenuOpen);
        open.setMargin(new Insets(3, 3, 3, 3));
        open.setPreferredSize(new Dimension(28, 28));
        open.setToolTipText(openSource == null ? "Course source navigation is not configured" : "Open course source");
        open.getAccessibleContext().setAccessibleName("Open course source");
        open.setEnabled(false);
        sourceList.addListSelectionListener(event -> open.setEnabled(openSource != null && sourceList.getSelectedValue() != null));
        open.addActionListener(event -> {
            if (openSource != null && sourceList.getSelectedValue() != null) {
                openSource.accept(sourceList.getSelectedValue());
            }
        });
        var sourceHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        sourceHeader.add(new JLabel("Sources"));
        sourceHeader.add(open);
        var sourcePanel = new JPanel(new BorderLayout(0, 4));
        sourcePanel.add(sourceHeader, BorderLayout.NORTH);
        sourcePanel.add(new JScrollPane(sourceList), BorderLayout.CENTER);
        var details = new JPanel(new BorderLayout(0, 8));
        details.add(new JScrollPane(detail), BorderLayout.CENTER);
        details.add(sourcePanel, BorderLayout.SOUTH);
        table.getSelectionModel().addListSelectionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                return;
            }
            var finding = review.findings().get(row);
            detail.setText(finding.explanation() + "\n\nSuggestion\n" + finding.suggestion());
            detail.setCaretPosition(0);
            citations.clear();
            finding.sources().forEach(citations::addElement);
            if (!citations.isEmpty()) {
                sourceList.setSelectedIndex(0);
            }
        });
        var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, summary, details);
        split.setResizeWeight(0.35);
        split.setDividerLocation(180);
        summary.setMinimumSize(new Dimension(200, 110));
        details.setMinimumSize(new Dimension(200, 230));
        panel.add(split, BorderLayout.CENTER);
        if (!review.findings().isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        return panel;
    }

    static String formatReview(CourseReview review) {
        var text = new StringBuilder(review.grounded() ? "Course-based review\n"
                : CourseReviewGenerator.GENERAL_KNOWLEDGE_NOTICE + "\n");
        for (var finding : review.findings()) {
            text.append("\n").append(finding.severity()).append(": ").append(finding.title())
                    .append("\n").append(finding.explanation())
                    .append("\nSuggestion: ").append(finding.suggestion()).append("\n");
            for (var source : finding.sources()) {
                text.append("Source: ").append(source.documentName()).append(" - p. ")
                        .append(source.slideNumber()).append("\n");
            }
        }
        if (review.findings().isEmpty()) {
            text.append("\nNo issues found in this review.\n");
        }
        return text.toString();
    }
}
