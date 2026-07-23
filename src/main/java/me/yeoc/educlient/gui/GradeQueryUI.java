package me.yeoc.educlient.gui;

import lombok.Getter;
import me.yeoc.educlient.object.GradeRecord;
import me.yeoc.educlient.service.GradeAnalyzeService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GradeQueryUI {
    private static final String[] COLUMNS = {
            "学年", "学期", "课程代码", "课程名称", "课程性质", "学分", "成绩", "绩点",
            "成绩性质", "学位课", "开课学院", "任课教师", "考核方式"
    };

    private final MainGUI mainGUI;
    private final GradeAnalyzeService analyzeService = new GradeAnalyzeService();
    private final List<GradeRecord> records = new ArrayList<>();

    @Getter
    private JPanel mainPanel;
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private JComboBox<AcademicYearOption> yearBox;
    private JComboBox<TermOption> termBox;
    private JButton queryButton;
    private JButton exportButton;
    private JLabel summaryLabel;
    private JProgressBar progressBar;

    public GradeQueryUI(MainGUI mainGUI) {
        this.mainGUI = mainGUI;
        initUI();
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        filters.add(new JLabel("学年："));
        yearBox = new JComboBox<>(buildAcademicYears());
        filters.add(yearBox);
        filters.add(new JLabel("学期："));
        termBox = new JComboBox<>(new TermOption[] {
                new TermOption("全部", ""), new TermOption("第一学期", "3"),
                new TermOption("第二学期", "12"), new TermOption("第三学期", "16")
        });
        filters.add(termBox);

        queryButton = new JButton("查询成绩");
        exportButton = new JButton("导出 CSV");
        exportButton.setEnabled(false);
        filters.add(queryButton);
        filters.add(exportButton);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(90, 18));
        progressBar.setVisible(false);
        filters.add(progressBar);
        mainPanel.add(filters, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        resultTable = new JTable(tableModel);
        resultTable.setAutoCreateRowSorter(true);
        resultTable.setRowHeight(24);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setColumnWidths();
        mainPanel.add(new JScrollPane(resultTable), BorderLayout.CENTER);

        summaryLabel = new JLabel("尚未查询");
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 2, 4));
        mainPanel.add(summaryLabel, BorderLayout.SOUTH);

        queryButton.addActionListener(event -> queryGrades());
        exportButton.addActionListener(event -> exportCsv());
    }

    private void queryGrades() {
        if (mainGUI.getEduService() == null) {
            JOptionPane.showMessageDialog(mainPanel, "请先在“个人信息”页完成登录。",
                    "未登录", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AcademicYearOption year = (AcademicYearOption) yearBox.getSelectedItem();
        TermOption term = (TermOption) termBox.getSelectedItem();
        setLoading(true);
        new Thread(() -> {
            try {
                String json = mainGUI.getEduService().fetchCourseData(
                        year == null ? "" : year.value(), term == null ? "" : term.value(), 5000);
                List<GradeRecord> result = analyzeService.parse(json);
                SwingUtilities.invokeLater(() -> showRecords(result));
            } catch (Exception error) {
                SwingUtilities.invokeLater(() -> {
                    mainGUI.info("成绩查询失败：" + error.getMessage());
                    JOptionPane.showMessageDialog(mainPanel, "成绩查询失败：" + error.getMessage(),
                            "查询失败", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> setLoading(false));
            }
        }, "grade-query").start();
    }

    private void showRecords(List<GradeRecord> result) {
        records.clear();
        records.addAll(result);
        tableModel.setRowCount(0);
        for (GradeRecord record : records) {
            tableModel.addRow(toRow(record));
        }
        exportButton.setEnabled(!records.isEmpty());

        double average = analyzeService.averageNumericScore(records);
        double gpa = analyzeService.weightedGpa(records);
        summaryLabel.setText(String.format(Locale.ROOT, "共 %d 门课程　平均分：%s　加权平均绩点：%s",
                records.size(), formatMetric(average), formatMetric(gpa)));
        mainGUI.info("成绩查询完成，共 " + records.size() + " 条记录。");
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("导出成绩");
        chooser.setSelectedFile(new java.io.File("成绩单.csv"));
        if (chooser.showSaveDialog(mainPanel) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        java.io.File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            file = new java.io.File(file.getParentFile(), file.getName() + ".csv");
        }
        if (file.exists() && JOptionPane.showConfirmDialog(mainPanel, "文件已存在，是否覆盖？", "确认覆盖",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append(String.join(",", COLUMNS)).append("\r\n");
        for (GradeRecord record : records) {
            Object[] row = toRow(record);
            for (int i = 0; i < row.length; i++) {
                if (i > 0) {
                    csv.append(',');
                }
                csv.append(csvCell(String.valueOf(row[i])));
            }
            csv.append("\r\n");
        }

        try {
            Files.writeString(file.toPath(), csv.toString(), StandardCharsets.UTF_8);
            mainGUI.info("成绩已导出到：" + file.getAbsolutePath());
            JOptionPane.showMessageDialog(mainPanel, "导出成功。", "完成", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException error) {
            JOptionPane.showMessageDialog(mainPanel, "导出失败：" + error.getMessage(),
                    "导出失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Object[] toRow(GradeRecord record) {
        return new Object[] { record.getAcademicYear(), record.getSemester(), record.getCourseCode(),
                record.getCourseName(), record.getCourseNature(), record.getCredits(), record.getScore(),
                record.getGradePoint(), record.getScoreNature(), record.getDegreeCourse(), record.getCollege(),
                record.getTeacher(), record.getAssessmentMethod() };
    }

    private AcademicYearOption[] buildAcademicYears() {
        int year = LocalDate.now().getYear();
        if (LocalDate.now().getMonthValue() < 8) {
            year--;
        }
        AcademicYearOption[] options = new AcademicYearOption[13];
        options[0] = new AcademicYearOption("全部", "");
        for (int i = 1; i < options.length; i++) {
            int start = year - i + 1;
            options[i] = new AcademicYearOption(start + "-" + (start + 1), String.valueOf(start));
        }
        return options;
    }

    private void setColumnWidths() {
        int[] widths = { 95, 55, 100, 190, 110, 55, 65, 55, 90, 65, 170, 100, 90 };
        for (int i = 0; i < widths.length; i++) {
            resultTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private void setLoading(boolean loading) {
        queryButton.setEnabled(!loading);
        yearBox.setEnabled(!loading);
        termBox.setEnabled(!loading);
        progressBar.setVisible(loading);
        if (loading) {
            summaryLabel.setText("正在查询成绩...");
        }
    }

    private String formatMetric(double value) {
        return Double.isNaN(value) ? "--" : String.format(Locale.ROOT, "%.2f", value);
    }

    private String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private record AcademicYearOption(String label, String value) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record TermOption(String label, String value) {
        @Override
        public String toString() {
            return label;
        }
    }
}
