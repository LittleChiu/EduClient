package me.yeoc.educlient.gui;

import lombok.Getter;
import me.yeoc.educlient.object.CourseTab;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

public class ChooseCourseGUI {
    private final MainGUI mainGUI;

    private JTabbedPane courseTabPane;
    private JButton updateButton;
    @Getter
    private JPanel mainPanel;

    public ChooseCourseGUI(MainGUI mainGUI) {
        this.mainGUI = mainGUI;
        initUI();
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout());

        // Top Panel: Actions
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        updateButton = new JButton("获取/刷新选课列表");
        topPanel.add(updateButton);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center: Tabs
        courseTabPane = new JTabbedPane();
        mainPanel.add(courseTabPane, BorderLayout.CENTER);

        // Listeners
        updateButton.addActionListener(e -> refreshTabs());
    }

    private void refreshTabs() {
        if (mainGUI.getEduService() == null) {
            JOptionPane.showMessageDialog(mainPanel, "请先在“个人信息”页登录!", "未登录", JOptionPane.WARNING_MESSAGE);
            return;
        }

        updateButton.setEnabled(false);
        new Thread(() -> {
            try {
                // Fetch in background
                List<CourseTab> tabs = mainGUI.getEduService().fetchCourseTabs();
                
                SwingUtilities.invokeLater(() -> {
                    courseTabPane.removeAll();
                    for (CourseTab courseTab : tabs) {
                         // Use CourseTabGUI for each tab
                        courseTabPane.addTab(courseTab.getName(), new CourseTabGUI(mainGUI, courseTab).getMainPanel());
                    }
                    mainGUI.info("选课列表刷新成功，共 " + tabs.size() + " 个类别。");
                });
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    mainGUI.info("刷新失败: " + ex.getMessage());
                    JOptionPane.showMessageDialog(mainPanel, "刷新失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> updateButton.setEnabled(true));
            }
        }).start();
    }
}
