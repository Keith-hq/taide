package edu.cst3492.taide.ui.chat;

import edu.cst3492.taide.service.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Native Swing workspace; no webview, browser runtime or HTML remote assets. */
public final class ChatPanel extends JPanel implements AutoCloseable {
    public interface Host {
        TaideService service();
        boolean demoMode();
        default String courseDirectory() { return ""; }
        void settings();
        void openCitation(Citation citation);
        default void openCitation(Citation citation, boolean demonstration) { openCitation(citation); }
        default void openCitation(Citation citation, boolean demonstration, String materialsDirectory) { openCitation(citation, demonstration); }
        void copy(String text);
        default Color color(String role, Color light) { return light; }
        default Font font() { return new Font("Microsoft YaHei UI", Font.PLAIN, 14); }
        default JScrollPane scrollPane(Component view) { return new JScrollPane(view); }
        /** Null opts out for standalone previews; an unchecked snapshot shows the initial check. */
        default SetupStatus setupStatus() { return null; }
        default void prepareModel() { settings(); }
        default void refreshSetup() { }
        default void useLiveMode() { settings(); }
    }
    private final Host host;
    private final ConversationController controller;
    private final MessageList messages = new MessageList();
    private final JPanel contextArea = new JPanel(new BorderLayout(8, 0));
    private final JLabel contextLabel = new JLabel();
    private final JLabel status = new JLabel("Ready to chat");
    private final JLabel mode = new JLabel();
    private final JPanel setup = new JPanel(new BorderLayout(0, 4));
    private JTextArea setupDetail;
    private final JLabel setupModel = new JLabel();
    private final JLabel setupApi = new JLabel();
    private final JLabel setupStart = new JLabel();
    private final JButton prepareModel = new JButton();
    private final JButton startLive = new JButton("Start live chat");
    private final JTextArea input = new JTextArea(3, 20) {
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getDocument().getLength() != 0) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(muted()); g.setFont(getFont());
            Insets insets = getInsets();
            g.drawString("Ask about your code…", insets.left + 1,
                    insets.top + g.getFontMetrics().getAscent());
            g.dispose();
        }
    };
    private final JButton send = new JButton("Send") {
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(isEnabled() ? text() : c("disabled", 0xE3E6E8));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            if (isFocusOwner()) {
                g.setColor(accent()); g.setStroke(new BasicStroke(1.5f));
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    };
    private final JButton retry = new JButton("Retry");
    private final JScrollPane scroll;
    private final Timer updates;
    private ConversationController.Snapshot pending;
    private ConversationController.Snapshot shown;
    private CodeContext context = CodeContext.empty();
    private boolean closed;
    private Color c(String role, int rgb) { return host.color(role, new Color(rgb)); }
    private Color ground() { return c("ground", 0xF9FBFC); }
    private Color surface() { return c("surface", 0xFFFFFF); }
    private Color text() { return c("text", 0x131416); }
    private Color muted() { return c("muted", 0x5D5F60); }
    private Color raised() { return c("raised", 0xF4F6F8); }
    private Color border() { return c("border", 0xE8EAEC); }
    private Color accent() { return c("accent", 0x1B6C80); }

    public ChatPanel(Host host) {
        super(new BorderLayout()); this.host = Objects.requireNonNull(host);
        setBackground(ground()); setBorder(new EmptyBorder(0, 8, 8, 8));
        setMinimumSize(new Dimension(280, 360));
        add(header(), BorderLayout.NORTH);
        JPanel sheet = round(surface(), 16);
        sheet.setLayout(new BorderLayout()); sheet.setBorder(new EmptyBorder(16, 16, 12, 16));
        add(sheet, BorderLayout.CENTER);
        JPanel modeBar = new JPanel(new BorderLayout(0, 8)); modeBar.setOpaque(false);
        modeBar.setBorder(new EmptyBorder(0, 0, 14, 0));
        mode.setFont(host.font().deriveFont(12f)); mode.setForeground(muted()); modeBar.add(mode, BorderLayout.NORTH);
        modeBar.add(setupGuide(), BorderLayout.CENTER);
        sheet.add(new CenteredLane(modeBar, 820), BorderLayout.NORTH);

        messages.setBackground(surface());
        scroll = host.scrollPane(messages);
        scroll.setBorder(null); scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(surface()); scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(6, 0));
        sheet.add(new CenteredLane(scroll, 820), BorderLayout.CENTER);
        sheet.add(new CenteredLane(composer(), 820), BorderLayout.SOUTH);
        controller = new ConversationController(SwingUtilities::invokeLater, snapshot -> {
            pending = snapshot;
        });
        updates = new Timer(60, event -> flush()); updates.start();
        render(controller.snapshot());
    }

    private JComponent header() {
        JPanel bar = new JPanel(new BorderLayout(8, 0)); bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(12, 8, 12, 8));
        JLabel name = label("TAIDE", 19, Font.BOLD); name.setIcon(new Mark(accent())); name.setIconTextGap(8);
        bar.add(name, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0)); actions.setOpaque(false);
        JButton fresh = secondary("New chat"); fresh.setToolTipText("Clear messages in this chat");
        fresh.addActionListener(e -> { controller.clear(); clearContext(); input.setText(""); input.requestFocusInWindow(); });
        JButton settings = secondary("Settings"); settings.addActionListener(e -> host.settings());
        actions.add(fresh); actions.add(settings); bar.add(actions, BorderLayout.EAST); return bar;
    }

    /** The next action stays visible while a download or conversation is in progress. */
    private JComponent setupGuide() {
        setup.setOpaque(false);
        setup.setBorder(new CompoundBorder(new LineBorder(border(), 1, true), new EmptyBorder(8, 10, 8, 10)));
        setup.setName("first-use-guide");
        JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false);
        heading.add(label("Set up live chat", 13, Font.BOLD));
        JButton refresh = secondary("Refresh"); refresh.setToolTipText("Check model, API settings and knowledge base again");
        refresh.addActionListener(e -> host.refreshSetup()); heading.add(refresh, BorderLayout.EAST);
        setup.add(heading, BorderLayout.NORTH);
        JPanel steps = new JPanel(new GridLayout(3, 1, 0, 0)); steps.setOpaque(false);
        styleSecondary(prepareModel); prepareModel.addActionListener(e -> host.prepareModel());
        prepareModel.setToolTipText("Download the retrieval model (about 90 MB), or choose an existing folder in Settings");
        steps.add(setupRow(setupModel, prepareModel));
        JButton configure = secondary("Settings"); configure.addActionListener(e -> host.settings());
        configure.setToolTipText("Configure the API, model and materials folders, then rebuild the knowledge base");
        steps.add(setupRow(setupApi, configure));
        styleSecondary(startLive); startLive.addActionListener(e -> host.useLiveMode());
        steps.add(setupRow(setupStart, startLive)); setup.add(steps, BorderLayout.CENTER);
        setupDetail = prose("", surface(), false);
        setupDetail.setFocusable(false);
        setupDetail.setFont(host.font().deriveFont(12f)); setupDetail.setForeground(muted());
        setup.add(setupDetail, BorderLayout.SOUTH);
        return setup;
    }

    private JComponent setupRow(JLabel label, JButton action) {
        JPanel row = new JPanel(new BorderLayout(4, 0)); row.setOpaque(false);
        label.setFont(host.font().deriveFont(12f)); label.setForeground(muted());
        label.setMinimumSize(new Dimension(0, 20)); row.add(label);
        action.setForeground(text()); row.add(action, BorderLayout.EAST);
        return row;
    }

    private void refreshSetupGuide() {
        SetupStatus state = host.setupStatus();
        boolean visible = state != null && (!state.ready() || host.demoMode());
        setup.setVisible(visible);
        if (!visible) return;
        setupModel.setText("1 Model · " + (state.preparing() ? "preparing" : !state.checked() ? "checking" : state.modelReady() ? "ready" : "needed"));
        prepareModel.setText(state.preparing() ? "Preparing…" : state.modelReady() ? "Ready" : "Download model");
        prepareModel.setEnabled(state.checked() && !state.modelReady() && !state.preparing());
        setupApi.setText("2 API & files · " + (!state.checked() ? "checking" : !state.apiReady() ? "set up" : state.indexReady() ? "ready" : "rebuild index"));
        setupStart.setText("3 " + (state.ready() ? "Ready to start" : "Finish setup first"));
        startLive.setEnabled(state.ready());
        String detail = !state.checked() && !state.preparing() ? "Checking local settings…" : state.detail().isBlank()
                ? state.ready() ? "Setup complete. Demo history keeps its sample labels." : "About 90 MB to download. Or choose a local model in Settings."
                : state.detail();
        setupDetail.setText(detail); setupDetail.setToolTipText(detail);
        setupModel.setToolTipText(setupModel.getText()); setupApi.setToolTipText(setupApi.getText());
        setup.revalidate(); setup.repaint();
    }

    private JComponent composer() {
        JPanel base = new JPanel(new BorderLayout(0, 10)); base.setOpaque(false);
        base.setBorder(new EmptyBorder(12, 0, 0, 0));
        contextArea.setOpaque(false); contextArea.setBorder(new EmptyBorder(0, 2, 0, 0));
        contextLabel.setFont(host.font().deriveFont(12f)); contextLabel.setForeground(muted());
        contextArea.add(contextLabel, BorderLayout.CENTER);
        JButton remove = secondary("Remove"); remove.addActionListener(e -> clearContext());
        remove.getAccessibleContext().setAccessibleName("Remove attached code context");
        contextArea.add(remove, BorderLayout.EAST); contextArea.setVisible(false);
        base.add(contextArea, BorderLayout.NORTH);

        JPanel box = round(raised(), 8); box.setLayout(new BorderLayout(0, 8));
        box.setBorder(new EmptyBorder(12, 12, 8, 12));
        input.setFont(host.font()); input.setForeground(text()); input.setBackground(raised());
        input.setCaretColor(text()); input.setLineWrap(true); input.setWrapStyleWord(true);
        input.setBorder(null); input.setToolTipText("Ask a question. Enter to send; Shift+Enter for a new line.");
        input.getAccessibleContext().setAccessibleName("Question");
        input.setTabSize(4);
        JScrollPane inputScroll = new JScrollPane(input); inputScroll.setBorder(null);
        inputScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        inputScroll.setPreferredSize(new Dimension(0, 64));
        box.add(inputScroll, BorderLayout.CENTER);
        JPanel controls = new JPanel(new BorderLayout(8, 0)); controls.setOpaque(false);
        JLabel hint = label("Enter sends · Shift+Enter newline", 12, Font.PLAIN); hint.setForeground(muted());
        controls.add(hint, BorderLayout.CENTER);
        send.setFont(host.font().deriveFont(Font.BOLD, 13f)); send.setForeground(surface());
        send.setOpaque(false); send.setContentAreaFilled(false); send.setFocusPainted(false);
        send.setBorder(new EmptyBorder(8, 14, 8, 14));
        send.addActionListener(e -> submitOrStop()); controls.add(send, BorderLayout.EAST);
        box.add(controls, BorderLayout.SOUTH); base.add(box, BorderLayout.CENTER);

        JPanel foot = new JPanel(new BorderLayout(8, 0)); foot.setOpaque(false);
        status.setFont(host.font().deriveFont(12f)); status.setForeground(muted());
        foot.add(status, BorderLayout.CENTER); retry.setVisible(false); styleSecondary(retry);
        retry.addActionListener(e -> { try { controller.retry(host.service(), host.demoMode(), host.courseDirectory()); } catch (RuntimeException ex) { showFailure(); } });
        foot.add(retry, BorderLayout.EAST); base.add(foot, BorderLayout.SOUTH);
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "send");
        input.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-break");
        input.getActionMap().put("send", new AbstractAction() { public void actionPerformed(ActionEvent e) { if (!controller.snapshot().busy()) submitOrStop(); } });
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateSend(); }
            public void removeUpdate(DocumentEvent e) { updateSend(); }
            public void changedUpdate(DocumentEvent e) { updateSend(); }
        });
        return base;
    }

    private void updateSend() { send.setEnabled(controller != null && (controller.snapshot().busy() || !input.getText().isBlank())); }
    private void submitOrStop() {
        if (controller.snapshot().busy()) { controller.cancel(); return; }
        try {
            if (controller.send(input.getText(), context, host.service(), host.demoMode(), host.courseDirectory())) input.setText("");
        } catch (RuntimeException ex) { showFailure(); }
    }
    private void showFailure() { status.setText("Chat service unavailable. Check Settings and retry."); status.setToolTipText(status.getText()); }

    /** Invoked on EDT after the editor snapshot was captured. */
    public void askAbout(CodeContext selected) {
        if (closed) return;
        context = selected == null ? CodeContext.empty() : selected;
        String file = context.filePath();
        try { file = Path.of(file).getFileName().toString(); } catch (RuntimeException ignored) { file = "Selected code"; }
        contextLabel.setText(file + " · L" + context.startLine() + "–" + context.endLine());
        contextLabel.setToolTipText(context.filePath() + " · " + context.enclosingClass());
        contextArea.setVisible(!context.selectedText().isBlank());
        input.setText("Explain this code using the reference materials and highlight anything I should watch out for.");
        if (!controller.snapshot().busy()) submitOrStop();
        else { status.setText("Code attached. Send after the current answer finishes."); input.requestFocusInWindow(); }
    }
    private void clearContext() { context = CodeContext.empty(); contextArea.setVisible(false); revalidate(); }
    private void flush() { if (pending != null) { var next = pending; pending = null; render(next); } }

    private void render(ConversationController.Snapshot snapshot) {
        if (closed) return;
        JScrollBar bar = scroll.getVerticalScrollBar();
        boolean firstQuestion = shown == null || shown.messages().isEmpty();
        boolean follow = bar.getMaximum() <= bar.getVisibleAmount() + 80
                || bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 60;
        int previousScroll = bar.getValue(); shown = snapshot;
        messages.removeAll();
        mode.setText("Demo mode · Sample answers and citations");
        mode.setVisible(host.demoMode());
        refreshSetupGuide();
        if (snapshot.messages().isEmpty()) welcome();
        else for (ConversationController.Message message : snapshot.messages()) addRow(message(message));
        messages.add(Box.createVerticalGlue());
        status.setText(snapshot.notice()); status.setToolTipText(snapshot.notice());
        send.setText(snapshot.busy() ? "Stop" : "Send");
        input.setEditable(!snapshot.busy());
        boolean error = !snapshot.messages().isEmpty() && snapshot.messages().getLast().status() == ConversationController.Status.ERROR;
        retry.setVisible(error); updateSend();
        messages.revalidate(); messages.repaint();
        SwingUtilities.invokeLater(() -> {
            if (closed) return;
            if (firstQuestion && !snapshot.messages().isEmpty()) bar.setValue(0);
            else if (follow) bar.setValue(bar.getMaximum()); else bar.setValue(previousScroll);
        });
    }

    private void welcome() {
        JPanel content = new JPanel(); content.setOpaque(false); content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(18, 0, 24, 0));
        JLabel title = label("Help beside your code", 22, Font.BOLD); title.setAlignmentX(LEFT_ALIGNMENT); content.add(title);
        content.add(Box.createVerticalStrut(12));
        content.add(prose("Ask a question, or select code and choose Ask TAIDE from the editor menu.", surface(), false));
        content.add(Box.createVerticalStrut(24));
        JLabel promptsLabel = label("Try asking", 12, Font.BOLD); promptsLabel.setForeground(muted());
        promptsLabel.setAlignmentX(LEFT_ALIGNMENT); content.add(promptsLabel);
        String[] prompts = {"Why use constructor injection?", "When does @Transactional fail?", "How do I test a service in isolation?"};
        for (String prompt : prompts) {
            JButton example = promptButton(prompt);
            example.setAlignmentX(LEFT_ALIGNMENT); example.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            example.addActionListener(e -> { input.setText(prompt); input.requestFocusInWindow(); });
            content.add(Box.createVerticalStrut(8)); content.add(example);
        }
        addRow(content);
    }

    private JComponent message(ConversationController.Message message) {
        JPanel outer = new JPanel(new BorderLayout()); outer.setOpaque(false); outer.setBorder(new EmptyBorder(0, 0, 22, 0));
        JPanel body = new JPanel(); body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS)); body.setOpaque(false);
        JPanel meta = new JPanel(new BorderLayout()); meta.setOpaque(false); meta.setBorder(new EmptyBorder(0, 0, 8, 0));
        JLabel who = label(message.user() ? "You" : "TAIDE", 12, Font.BOLD); who.setForeground(message.user() ? muted() : accent());
        meta.add(who, message.user() ? BorderLayout.EAST : BorderLayout.WEST);
        if (!message.user() && !message.text().isEmpty()) {
            JButton copy = secondary("Copy"); copy.addActionListener(e -> { host.copy(message.text()); copy.setText("Copied");
                Timer timer = new Timer(1600, ignored -> copy.setText("Copy")); timer.setRepeats(false); timer.start(); });
            meta.add(copy, BorderLayout.EAST);
        }
        meta.setAlignmentX(message.user() ? RIGHT_ALIGNMENT : LEFT_ALIGNMENT); body.add(meta);
        if (message.user()) {
            JPanel bubble = round(raised(), 8); bubble.setLayout(new BorderLayout()); bubble.setBorder(new EmptyBorder(12, 14, 12, 14));
            bubble.add(prose(message.text(), raised(), false)); bubble.setMaximumSize(new Dimension(640, Integer.MAX_VALUE));
            bubble.setAlignmentX(RIGHT_ALIGNMENT); body.add(bubble);
            if (!message.context().selectedText().isBlank()) {
                JLabel attached = label("Code attached · L" + message.context().startLine() + "–" + message.context().endLine(), 12, Font.PLAIN);
                attached.setForeground(muted()); attached.setAlignmentX(RIGHT_ALIGNMENT); body.add(Box.createVerticalStrut(8)); body.add(attached);
            }
        } else {
            String value = message.text().isEmpty() && message.status() == ConversationController.Status.STREAMING ? "Looking through reference materials…" : message.text();
            JComponent answer = answer(value); answer.setAlignmentX(LEFT_ALIGNMENT); body.add(answer);
            if (message.status() == ConversationController.Status.COMPLETE) {
                body.add(Box.createVerticalStrut(14));
                if (!message.grounded() || message.citations().isEmpty()) {
                    JPanel warning = round(c("warning-bg", 0xFFFBEB), 6); warning.setLayout(new BorderLayout());
                    warning.setBorder(new EmptyBorder(10, 12, 10, 12));
                    JComponent note = prose("General knowledge answer. No source materials cited; verify against your references.", c("warning-bg", 0xFFFBEB), false);
                    note.setForeground(c("warning", 0x92400E)); warning.add(note); warning.setAlignmentX(LEFT_ALIGNMENT); body.add(warning);
                } else {
                    int count = message.citations().size();
                    JLabel heading = label("Sources · " + count + (count == 1 ? " citation" : " citations"), 12, Font.BOLD);
                    heading.setForeground(muted()); heading.setAlignmentX(LEFT_ALIGNMENT); body.add(heading);
                    for (Citation citation : message.citations()) { body.add(Box.createVerticalStrut(8)); body.add(citation(citation, message.demo(), message.courseDirectory())); }
                }
                if (!message.model().isBlank()) {
                    JLabel model = label(message.model(), 12, Font.PLAIN); model.setForeground(muted()); model.setAlignmentX(LEFT_ALIGNMENT);
                    body.add(Box.createVerticalStrut(10)); body.add(model);
                }
            }
            if (message.status() == ConversationController.Status.CANCELLED || message.status() == ConversationController.Status.ERROR) {
                JLabel state = label(message.status() == ConversationController.Status.CANCELLED ? "Generation stopped" : "Answer interrupted. You can retry.", 12, Font.PLAIN);
                state.setForeground(muted()); state.setAlignmentX(LEFT_ALIGNMENT); body.add(Box.createVerticalStrut(8)); body.add(state);
            }
        }
        outer.add(body); return outer;
    }

    private JComponent citation(Citation citation, boolean demonstration, String materialsDirectory) {
        JButton card = new JButton(); card.setLayout(new BorderLayout(0, 6)); card.setBackground(surface());
        card.setForeground(text()); card.setOpaque(true); card.setFocusPainted(true);
        card.setBorder(new CompoundBorder(new LineBorder(border(), 1, true), new EmptyBorder(10, 12, 10, 12)));
        String name = Objects.requireNonNullElse(citation.documentName(), "Untitled document");
        JPanel line = new JPanel(new BorderLayout(0, 4)); line.setOpaque(false);
        JLabel file = label(name, 12, Font.BOLD); file.setToolTipText(name);
        file.setMinimumSize(new Dimension(0, file.getPreferredSize().height)); line.add(file, BorderLayout.NORTH);
        String citationDetail = demonstration ? "Sample citation" : Double.isFinite(citation.score())
                ? "Similarity " + Math.round(Math.max(0, Math.min(1, citation.score())) * 100) + "%" : "Similarity unavailable";
        String location = citation.slideNumber() > 0 ? "Page " + citation.slideNumber() + "" : "Page unavailable";
        JLabel page = label(location + " · " + citationDetail, 12, Font.PLAIN);
        page.setForeground(muted()); line.add(page, BorderLayout.CENTER); card.add(line, BorderLayout.NORTH);
        JComponent snippet = prose(Objects.requireNonNullElse(citation.snippet(), ""), surface(), false); snippet.setForeground(muted());
        // Child text remains selectable; open is also available by keyboard on the whole card.
        card.add(snippet, BorderLayout.CENTER);
        card.setToolTipText(demonstration ? "Sample data for citation preview" : "Open source file. Similarity is a retrieval score, not answer accuracy.");
        card.getAccessibleContext().setAccessibleName("Open " + name + ", page " + citation.slideNumber() + "");
        card.addActionListener(e -> host.openCitation(citation, demonstration, materialsDirectory));
        file.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { host.openCitation(citation, demonstration, materialsDirectory); } });
        page.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { host.openCitation(citation, demonstration, materialsDirectory); } });
        card.setAlignmentX(LEFT_ALIGNMENT); return card;
    }

    private JTextArea prose(String value, Color background, boolean code) {
        JTextArea area = new JTextArea(value) {
            @Override public Dimension getPreferredSize() {
                int width = getParent() == null ? 300 : getParent().getWidth();
                if (width <= 0) width = Math.max(240, ChatPanel.this.getWidth() - 72);
                Insets in = getParent() instanceof JComponent jc ? jc.getInsets() : new Insets(0, 0, 0, 0);
                width = Math.max(160, width - in.left - in.right);
                setSize(width, Short.MAX_VALUE);
                Dimension preferred = super.getPreferredSize();
                return new Dimension(width, preferred.height);
            }
        };
        area.setFont(code ? new Font(Font.MONOSPACED, Font.PLAIN, 13) : host.font());
        area.setForeground(text()); area.setBackground(background); area.setEditable(false);
        area.setLineWrap(true); area.setWrapStyleWord(true); area.setBorder(null); area.setFocusable(true);
        area.setAlignmentX(LEFT_ALIGNMENT); ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        return area;
    }
    private JComponent answer(String value) {
        JPanel parts = new JPanel(); parts.setOpaque(false); parts.setLayout(new BoxLayout(parts, BoxLayout.Y_AXIS));
        for (AnswerParts.Part part : AnswerParts.parse(value)) {
            JComponent block;
            if (part.code()) {
                JPanel code = round(raised(), 6); code.setLayout(new BorderLayout());
                code.setBorder(new EmptyBorder(10, 12, 10, 12));
                code.add(prose(part.text(), raised(), true)); block = code;
                if (parts.getComponentCount() > 0) parts.add(Box.createVerticalStrut(10));
            } else {
                block = prose(part.text(), surface(), false);
                if (parts.getComponentCount() > 0) parts.add(Box.createVerticalStrut(8));
            }
            block.setAlignmentX(LEFT_ALIGNMENT); parts.add(block);
        }
        if (parts.getComponentCount() == 0) parts.add(prose(value, surface(), false));
        return parts;
    }
    private void addRow(JComponent component) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        JPanel wrapper = new JPanel(new BorderLayout()) {
            @Override public Dimension getPreferredSize() {
                int width = messages.getWidth() > 0 ? messages.laneWidth()
                        : Math.max(160, ChatPanel.this.getWidth() - 64);
                component.setSize(width, Math.max(1, component.getHeight()));
                layoutForWidth(component);
                return new Dimension(width, component.getPreferredSize().height);
            }
        };
        wrapper.setOpaque(false); wrapper.add(component);
        wrapper.setMinimumSize(new Dimension(0, 0));
        wrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        messages.add(wrapper);
    }
    /** Wrapped text height is a function of the current lane width, including after dock resizing. */
    private static void layoutForWidth(Container parent) {
        parent.invalidate(); parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container nested) layoutForWidth(nested);
        parent.invalidate();
    }
    private JLabel label(String text, int size, int weight) {
        JLabel label = new JLabel(text); label.setFont(host.font().deriveFont(weight, (float) size)); label.setForeground(text()); return label;
    }
    private JButton secondary(String text) { JButton button = new JButton(text); styleSecondary(button); return button; }
    private JButton promptButton(String text) {
        JButton button = new JButton(text + "   ↗") {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(getModel().isRollover() ? raised() : surface());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g.setColor(border()); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g.dispose(); super.paintComponent(graphics);
            }
        };
        button.setFont(host.font().deriveFont(13f)); button.setForeground(text());
        button.setHorizontalAlignment(SwingConstants.LEFT); button.setContentAreaFilled(false);
        button.setOpaque(false); button.setFocusPainted(true); button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(10, 12, 10, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
    private void styleSecondary(JButton button) {
        button.setFont(host.font().deriveFont(12f)); button.setForeground(muted()); button.setOpaque(false);
        button.setContentAreaFilled(false); button.setFocusPainted(true); button.setBorder(new EmptyBorder(6, 8, 6, 8));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private JPanel round(Color color, int radius) {
        return new JPanel() {
            { setOpaque(false); }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(color); g.fillRoundRect(0, 0, getWidth(), getHeight(), radius * 2, radius * 2); g.dispose();
                super.paintComponent(graphics);
            }
        };
    }
    private record Mark(Color color) implements Icon {
        public int getIconWidth() { return 24; } public int getIconHeight() { return 24; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create(); g.translate(x, y); g.setColor(color);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(3, 6, 20, 6); g.drawLine(12, 6, 12, 20); g.drawLine(3, 11, 7, 15); g.drawLine(7, 15, 3, 19); g.dispose();
        }
    }
    private static final class CenteredLane extends JPanel {
        private final Component content;
        private final int maximumWidth;
        CenteredLane(Component content, int maximumWidth) {
            this.content = content; this.maximumWidth = maximumWidth;
            setOpaque(false); setLayout(null); add(content);
        }
        @Override public Dimension getPreferredSize() {
            Dimension preferred = content.getPreferredSize();
            return new Dimension(Math.min(maximumWidth, Math.max(360, preferred.width)), preferred.height);
        }
        @Override public Dimension getMinimumSize() { return new Dimension(0, content.getMinimumSize().height); }
        @Override public void doLayout() {
            int width = Math.min(maximumWidth, getWidth());
            content.setBounds((getWidth() - width) / 2, 0, width, getHeight());
        }
    }
    private static final class MessageList extends JPanel implements Scrollable {
        MessageList() {
            setLayout(null);
            setBorder(new EmptyBorder(0, 4, 0, 16));
        }
        private int laneWidth() {
            Insets insets = getInsets();
            return Math.min(820, Math.max(0, getWidth() - insets.left - insets.right));
        }
        @Override public Dimension getPreferredSize() {
            int height = 0;
            for (Component child : getComponents())
                if (!(child instanceof Box.Filler)) height += child.getPreferredSize().height;
            return new Dimension(820, height);
        }
        @Override public void doLayout() {
            Insets insets = getInsets();
            int width = laneWidth();
            int x = insets.left + (getWidth() - insets.left - insets.right - width) / 2;
            int y = insets.top;
            Component[] children = getComponents();
            for (int index = 0; index < children.length; index++) {
                Component child = children[index];
                int height = child instanceof Box.Filler
                        ? Math.max(0, getHeight() - insets.bottom - y) : child.getPreferredSize().height;
                child.setBounds(x, y, width, height);
                y += height;
            }
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(24, visibleRect.height - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
    /** Screenshot and smoke-test access to the real native component. */
    public ConversationController conversation() { return controller; }
    /** Model progress changes update the compact guide without rebuilding conversation rows. */
    public void refreshSetupStatus() { if (!closed) { refreshSetupGuide(); revalidate(); repaint(); } }
    public void refreshMode() { if (!closed) { pending = null; render(controller.snapshot()); } }
    @Override public void close() { closed = true; updates.stop(); controller.close(); }
}
