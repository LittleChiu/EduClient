package me.yeoc.educlient.gui;

import lombok.Getter;
import me.yeoc.educlient.service.GradeAnalyzeService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class GradeQueryUI {
    private final MainGUI mainGUI;
    private GradeAnalyzeService service;

    @Getter
    private JPanel mainPanel;
    private JTable resultTable;
    private JButton queryButton;
    private JButton exportButton;

    public GradeQueryUI(MainGUI mainGUI) {
        this.mainGUI = mainGUI;
        initUI();
    }

    private void initUI() {
        service = new GradeAnalyzeService();
        mainPanel = new JPanel(new BorderLayout());

        // Top Panel
        JPanel topPanel = new JPanel(new FlowLayout());
        queryButton = new JButton("查询成绩 (Mock)");
        exportButton = new JButton("导出 Excel (Mock)");
        topPanel.add(queryButton);
        topPanel.add(exportButton);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center Table
        String[] columnNames = { "学年", "学期", "课程名称", "学分", "成绩", "绩点" };
        resultTable = new JTable(new DefaultTableModel(columnNames, 0));
        mainPanel.add(new JScrollPane(resultTable), BorderLayout.CENTER);

        // Listeners (Placeholders)
        queryButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(mainPanel, "功能开发中...");
            mainGUI.info("点击了查询成绩");
        });

        exportButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(mainPanel, "功能开发中...");
        });
    }
}
