package com.stylo.gui;

import com.stylo.CliMain;
import com.stylo.core.AnalysisResult;
import com.stylo.core.ReportBuilder;
import com.stylo.core.StylometryEngine;
import com.stylo.core.TextDocument;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

public class MainWindow extends JFrame {

    private final JTextField path1 = new JTextField();
    private final JTextField path2 = new JTextField();
    private final JLabel info1 = new JLabel("не выбран");
    private final JLabel info2 = new JLabel("не выбран");
    private final JLabel status = new JLabel(" ");
    private final JTextArea result = new JTextArea();
    private final JButton analyzeBtn = new JButton("Сравнить");
    private final JButton saveBtn = new JButton("Сохранить");
    private final JButton copyBtn = new JButton("Копировать");
    private final TextDropHandler dropHandler = new TextDropHandler();

    private TextDocument doc1;
    private TextDocument doc2;

    public MainWindow() {
        super("Стилометрия");
        configureLookAndFeel();
        buildUi();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(880, 640);
        setMinimumSize(new Dimension(700, 520));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void configureLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo laf : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(laf.getName())) { UIManager.setLookAndFeel(laf.getClassName()); break; }
            }
        } catch (Exception ignore) { }
    }

    private void buildUi() {
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 4, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = 0; g.gridwidth = 3; g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(0, 0, 6, 0);
        JLabel hint = new JLabel("Перетащите файлы в окно или нажмите «Обзор».");
        hint.setForeground(new Color(110, 110, 110));
        top.add(hint, g);

        addFileRow(top, 1, "Текст 1", path1, info1);
        addFileRow(top, 2, "Текст 2", path2, info2);

        g.gridx = 0; g.gridy = 8; g.gridwidth = 3; g.anchor = GridBagConstraints.WEST;
        status.setForeground(new Color(0x8A6D00));
        top.add(status, g);

        g.gridy = 9; g.anchor = GridBagConstraints.CENTER; g.insets = new Insets(6, 0, 2, 0);
        analyzeBtn.setFont(analyzeBtn.getFont().deriveFont(Font.BOLD, 13f));
        analyzeBtn.setPreferredSize(new Dimension(180, 34));
        analyzeBtn.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) { onAnalyze(); }
        });
        top.add(analyzeBtn, g);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(4, 10, 10, 10));
        result.setEditable(false);
        result.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(result);
        bottom.add(scroll, BorderLayout.CENTER);

        JPanel savePanel = new JPanel(new BorderLayout());
        saveBtn.setEnabled(false);
        copyBtn.setEnabled(false);
        saveBtn.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) { onSave(); }
        });
        copyBtn.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) { onCopy(); }
        });
        JPanel btns = new JPanel();
        btns.add(saveBtn);
        btns.add(copyBtn);
        savePanel.add(btns, BorderLayout.EAST);
        bottom.add(savePanel, BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(bottom, BorderLayout.CENTER);
        attachDrop(getContentPane());
    }

    private void attachDrop(Container c) {
        if (c instanceof JComponent) ((JComponent) c).setTransferHandler(dropHandler);
        for (Component child : c.getComponents()) {
            if (child instanceof Container) attachDrop((Container) child);
        }
    }

    private void addFileRow(JPanel top, int slot, String label, final JTextField pathField, final JLabel infoLabel) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = slot == 1 ? 1 : 4;
        g.gridx = 0; g.anchor = GridBagConstraints.WEST; g.insets = new Insets(2, 0, 2, 8);
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        top.add(l, g);

        g.gridx = 1; g.weightx = 1.0; g.fill = GridBagConstraints.HORIZONTAL; g.insets = new Insets(2, 0, 2, 8);
        pathField.setEditable(false);
        top.add(pathField, g);

        g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE; g.insets = new Insets(2, 0, 2, 0);
        JButton browse = new JButton("Обзор…");
        browse.addActionListener(new AbstractAction() {
            public void actionPerformed(ActionEvent e) { onBrowse(slot); }
        });
        top.add(browse, g);

        g.gridx = 0; g.gridwidth = 3; g.gridy = slot == 1 ? 2 : 5; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        infoLabel.setForeground(new Color(100, 100, 100));
        top.add(infoLabel, g);
    }

    private void onBrowse(int slot) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Текст " + slot);
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Текстовые файлы (*.txt, *.md, *.csv, *.log, *.xml, *.html)",
                "txt", "md", "csv", "tsv", "log", "json", "xml", "html", "htm");
        fc.setFileFilter(filter);
        fc.setAcceptAllFileFilterUsed(true);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        loadFileIntoSlot(fc.getSelectedFile(), slot);
    }

    private void loadFileIntoSlot(File f, int slot) {
        TextDocument doc;
        try {
            doc = TextDocument.fromFile(f);
        } catch (TextDocument.NotTextException ex) {
            JOptionPane.showMessageDialog(this,
                    "«" + f.getName() + "» не текстовый файл (" + ex.getMessage() + ").",
                    "Не текстовый файл", JOptionPane.ERROR_MESSAGE);
            return;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Не удалось прочитать файл:\n" + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
            return;
        }
        setSlot(slot, doc, f.getAbsolutePath());
    }

    private void setSlot(int slot, TextDocument doc, String display) {
        if (slot == 1) { doc1 = doc; path1.setText(display); info1.setText(formatInfo(doc)); }
        else { doc2 = doc; path2.setText(display); info2.setText(formatInfo(doc)); }
        status.setText("Текст " + slot + " ← " + doc.wordCount + " слов");
        checkLengthAlert(doc, String.valueOf(slot));
    }

    private int nextDefaultSlot() {
        if (doc1 == null) return 1;
        if (doc2 == null) return 2;
        return 1;
    }

    private int chooseSlot(String name, int def) {
        Object[] opts = { "Текст 1", "Текст 2", "Отмена" };
        int ans = JOptionPane.showOptionDialog(this,
                "Куда поместить «" + name + "»?",
                "Выбор слота", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, opts, opts[def == 2 ? 1 : 0]);
        if (ans == 0) return 1;
        if (ans == 1) return 2;
        return -1;
    }

    private String formatInfo(TextDocument d) {
        return d.wordCount + " слов, " + d.scriptDescription() + ", " + d.charsetUsed;
    }

    private boolean checkLengthAlert(TextDocument doc, String which) {
        if (doc.wordCount >= StylometryEngine.SHORT_WORDS_THRESHOLD) return true;
        String extra = doc.wordCount < StylometryEngine.CRITICAL_WORDS_THRESHOLD
                ? "\nСлов совсем мало, результат почти случайный."
                : "";
        int ans = JOptionPane.showConfirmDialog(this,
                "В тексте " + which + " всего " + doc.wordCount + " слов, меньше 500.\n"
                        + "Точность будет низкой. Продолжить?" + extra,
                "Короткий текст",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        boolean ok = ans == JOptionPane.OK_OPTION;
        status.setText(ok ? "Короткий текст " + which + " подтверждён" : " ");
        return ok;
    }

    private boolean checkScriptMismatch() {
        if (doc1 == null || doc2 == null) return true;
        if (!TextDocument.scriptsFundamentallyDiffer(doc1, doc2)) return true;
        int ans = JOptionPane.showConfirmDialog(this,
                "Разные письменности:\n1: " + doc1.scriptDescription() + "\n2: " + doc2.scriptDescription()
                        + "\n\nПродолжить?",
                "Разные языки",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return ans == JOptionPane.OK_OPTION;
    }

    private void onAnalyze() {
        if (doc1 == null || doc2 == null) {
            JOptionPane.showMessageDialog(this, "Выберите оба текста.",
                    "Файлы не выбраны", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (doc1.wordCount < StylometryEngine.SHORT_WORDS_THRESHOLD && !checkLengthAlert(doc1, "1")) return;
        if (doc2.wordCount < StylometryEngine.SHORT_WORDS_THRESHOLD && !checkLengthAlert(doc2, "2")) return;
        if (!checkScriptMismatch()) return;

        final TextDocument a = doc1, b = doc2;
        analyzeBtn.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        status.setText("Анализ…");

        new SwingWorker<AnalysisResult, Void>() {
            @Override protected AnalysisResult doInBackground() throws Exception {
                return StylometryEngine.analyze(a, b);
            }
            @Override protected void done() {
                setCursor(Cursor.getDefaultCursor());
                analyzeBtn.setEnabled(true);
                try {
                    AnalysisResult r = get();
                    result.setText(ReportBuilder.build(r));
                    result.setCaretPosition(0);
                    saveBtn.setEnabled(true);
                    copyBtn.setEnabled(true);
                    status.setText(String.format(java.util.Locale.US, "Готово: %.1f %% (%s)",
                            r.probability * 100, r.verdict));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainWindow.this,
                            "Ошибка анализа: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                    status.setText(" ");
                }
            }
        }.execute();
    }

    private void onSave() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Сохранить отчёт");
        fc.setSelectedFile(new File("Стилометрический_отчёт.txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        try {
            CliMain.writeUtf8Bom(f, result.getText());
            status.setText("Отчёт сохранён: " + f.getName());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Не удалось сохранить:\n" + ex.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onCopy() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(result.getText()), null);
        status.setText("Отчёт скопирован");
    }

    private class TextDropHandler extends TransferHandler {

        public int getSourceActions(JComponent c) {
            return c == result ? COPY : NONE;
        }

        protected Transferable createTransferable(JComponent c) {
            if (c != result) return null;
            String sel = result.getSelectedText();
            return new StringSelection(sel != null && !sel.isEmpty() ? sel : result.getText());
        }

        public boolean canImport(TransferSupport support) {
            DataFlavor[] flavors = support.getDataFlavors();
            for (DataFlavor f : flavors) {
                if (f.equals(DataFlavor.javaFileListFlavor) || f.equals(DataFlavor.stringFlavor)) return true;
            }
            return false;
        }

        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            Transferable t = support.getTransferable();
            try {
                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    List<?> files = (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    handleDroppedFiles(files);
                    return true;
                }
                if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    Object data = t.getTransferData(DataFlavor.stringFlavor);
                    if (data instanceof String) {
                        handleDroppedText((String) data);
                        return true;
                    }
                }
            } catch (Exception ignore) { }
            return false;
        }
    }

    private void handleDroppedFiles(List<?> files) {
        if (files.isEmpty()) return;
        if (files.size() > 2) {
            JOptionPane.showMessageDialog(this, "Не более двух файлов за раз.",
                    "Drag and Drop", JOptionPane.WARNING_MESSAGE);
            return;
        }
        for (Object o : files) {
            if (!(o instanceof File)) continue;
            File f = (File) o;
            int slot = chooseSlot(f.getName(), nextDefaultSlot());
            if (slot == -1) break;
            loadFileIntoSlot(f, slot);
        }
    }

    private void handleDroppedText(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Пустой фрагмент.",
                    "Drag and Drop", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String oneLine = trimmed.replace('\n', ' ').replace('\r', ' ').trim();
        String preview = oneLine.length() > 40 ? oneLine.substring(0, 40) + "…" : oneLine;
        int slot = chooseSlot(preview, nextDefaultSlot());
        if (slot == -1) return;
        TextDocument doc = TextDocument.fromString("вставленный текст", trimmed);
        setSlot(slot, doc, "вставленный текст (" + doc.wordCount + " слов)");
    }
}
