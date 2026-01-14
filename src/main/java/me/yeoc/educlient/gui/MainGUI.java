package me.yeoc.educlient.gui;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import me.yeoc.educlient.service.EduService;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.util.Properties;

public class MainGUI {
    @Getter
    private JPanel mainPanel;
    @Getter
    @Setter
    private EduService eduService;
    private JTabbedPane tabbedPane;
    private JTextArea logArea;

    @SneakyThrows
    public static void start() {
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        } catch (Exception e) {
            e.printStackTrace();
        }

        String title = "教务管理系统 学生客户端 (Dev Mode)";
        try (InputStream is = MainGUI.class.getClassLoader().getResourceAsStream("git.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String ver = props.getProperty("git.commit.id.abbrev", "unknown");
                String time = props.getProperty("git.build.time", "unknown");
                title = String.format("教务管理系统 学生客户端 (v%s built at %s)", ver, time);
            }
        } catch (Exception ignored) {
        }

        JFrame frame = new JFrame(title);
        frame.setContentPane(new MainGUI().mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1280, 800);
        frame.setLocationRelativeTo(null); // Center
        frame.setVisible(true);
    }

    public MainGUI() {
        initUI();
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout());

        // Center: Tabbed Pane
        tabbedPane = new JTabbedPane();
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Bottom: Log Area
        logArea = new JTextArea();
        logArea.setRows(8);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Run Log"));
        mainPanel.add(scrollPane, BorderLayout.SOUTH);

        // Add Tabs
        tabbedPane.addTab("选课管理", new ChooseCourseGUI(this).getMainPanel());
        tabbedPane.addTab("监控已选", new MonitorPanel(this).getMainPanel());
        tabbedPane.addTab("批量抢课", new BatchSnatchPanel(this).getMainPanel());
        tabbedPane.addTab("成绩查询", new GradeQueryUI(this).getMainPanel());
        tabbedPane.addTab("个人信息", new UserInfoPanel(this).getMainPanel());
    }

    public void info(String str) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(str + "\n");
            try {
                logArea.setCaretPosition(logArea.getDocument().getLength());
            } catch (Exception ignored) {
            }
        });
    }
}
