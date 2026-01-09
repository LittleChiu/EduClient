package me.yeoc.educlient.gui;

import me.yeoc.educlient.object.CourseItem;
import me.yeoc.educlient.object.CourseTab;
import me.yeoc.educlient.service.EduService;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BatchSnatchPanel {
    private final MainGUI mainGUI;
    private JPanel mainPanel;
    private JTextArea inputArea;
    private JTextArea logArea;
    private JButton startButton;
    private JButton stopButton;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BatchSnatchPanel(MainGUI mainGUI) {
        this.mainGUI = mainGUI;
        initUI();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout());

        // Left: Input
        inputArea = new JTextArea();
        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(new TitledBorder("教学班ID列表 (一行一个)"));

        // Right: Log
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("抢课日志"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputScroll, logScroll);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.3);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Top: Toolbar
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startButton = new JButton("开始批量抢课");
        stopButton = new JButton("停止");
        stopButton.setEnabled(false);
        topPanel.add(startButton);
        topPanel.add(stopButton);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Listeners
        startButton.addActionListener(e -> startBatchSnatch());
        stopButton.addActionListener(e -> {
            running.set(false);
            log("正在停止...");
            stopButton.setEnabled(false);
        });
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            try {
                logArea.setCaretPosition(logArea.getDocument().getLength());
            } catch (Exception ignored) {
            }
        });
    }

    private void startBatchSnatch() {
        String input = inputArea.getText().trim();
        if (input.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "请输入教学班ID！");
            return;
        }

        String[] ids = input.split("\\r?\\n");
        if (ids.length == 0)
            return;

        EduService service = mainGUI.getEduService();
        if (service == null) {
            JOptionPane.showMessageDialog(mainPanel, "请先在个人信息页登录！");
            return;
        }

        running.set(true);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        log("=== 开始批量抢课任务，共 " + ids.length + " 个目标 ===");

        new Thread(() -> {
            try {
                // 1. Fetch tabs once
                log("正在获取课程类别(Tabs)...");
                List<CourseTab> tabs = service.fetchCourseTabs();
                log("获取到 " + tabs.size() + " 个课程类别。");

                // 2. Loop through IDs
                for (String id : ids) {
                    if (!running.get())
                        break;
                    id = id.trim();
                    if (id.isEmpty())
                        continue;

                    processSingleId(service, tabs, id);

                    // Small delay to be nice
                    Thread.sleep(500);
                }
                log("=== 批量任务结束 ===");
            } catch (Exception e) {
                log("任务异常终止: " + e.getMessage());
                e.printStackTrace();
            } finally {
                running.set(false);
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                });
            }
        }).start();
    }

    private void processSingleId(EduService service, List<CourseTab> tabs, String keyword) {
        log("--------------------------------------------------");
        log("正在处理关键词: " + keyword);
        boolean foundAny = false;

        for (CourseTab tab : tabs) {
            if (!running.get())
                return;
            try {
                // Determine bklx_id if needed (lazy load)
                if (tab.getBklxId() == null) {
                    service.fetchCourseTabMetadata(tab);
                }

                // Search in this tab
                List<CourseItem> courses = service.fetchCourseList(tab, keyword);
                if (courses.isEmpty())
                    continue;

                foundAny = true;
                log("  [类别: " + tab.getName() + "] 找到 " + courses.size() + " 门相关课程");

                for (CourseItem courseHead : courses) {
                    if (!running.get())
                        return;
                    processCourseHead(service, tab, keyword, courseHead);
                }

            } catch (Exception e) {
                log("  [类别: " + tab.getName() + "] 查询出错: " + e.getMessage() + " (已跳过)");
            }
        }

        if (!foundAny) {
            log("未在任何类别中找到匹配课程: " + keyword);
        }
    }

    private void processCourseHead(EduService service, CourseTab tab, String keyword, CourseItem courseHead) {
        try {
            // We need to fetch details to get the actual classes (JXB)
            // Context items are usually needed. In CourseTabGUI we limit them, here let's
            // try passing empty or just the head.
            // Based on CourseTabGUI logic, we should group by kchId, but here we just have
            // one list.
            // Let's pass the single head item as context.
            List<CourseItem> context = Collections.singletonList(courseHead);

            List<CourseItem> details = service.fetchCourseDetails(tab, keyword, courseHead.getKchId(), context);
            if (details.isEmpty()) {
                log("    课程 [" + courseHead.getKcmc() + "] 无教学班信息");
                return;
            }

            for (CourseItem detail : details) {
                if (!running.get())
                    return;
                // Match? If the user input IS the JXB Name (e.g. (2024-xxx)-01), we might want
                // to filter?
                // But the user said "Teaching Class as keyword search", so
                // fetchCourseList(keyword) filters already?
                // fetchCourseList filters by Course Name or Code usually.
                // PartDisplay.html filter_list[0] usually filters broadly.
                // Let's check if the detail name contains the keyword?
                // User said: "Teaching Class as keyword search, generally only one class found"
                // So we assume the details returned ARE what we want, or we try to enroll in
                // ALL of them that match.

                // Check Capacity
                int limit = parseInt(detail.getJxbrl());
                int used = parseInt(detail.getYxzrs());

                log("    发现教学班: " + detail.getJxbmc() + " | 容量: " + used + "/" + limit);

                if (used >= limit) {
                    log("      -> 人数已满 (" + used + "/" + limit + ")，但这不重要，直接抢！");
                }

                // Sub-course Logic
                String jxbIds = service.getSmartJxbIds(tab, detail);
                if (jxbIds != null) {
                    log("      ★ 关联子课程检测: 需同时选多个班 (ID组合: " + jxbIds + ")");
                }

                // Try Enroll
                attemptEnroll(service, tab, detail, jxbIds);
            }

        } catch (Exception e) {
            log("    处理课程 [" + courseHead.getKcmc() + "] 详情时出错: " + e.getMessage());
        }
    }

    private void attemptEnroll(EduService service, CourseTab tab, CourseItem detail, String overrideJxbIds) {
        try {
            log("      正在尝试抢课/选课...");
            boolean success;
            if (overrideJxbIds != null) {
                success = service.enrollCourse(tab, detail, overrideJxbIds);
            } else {
                success = service.enrollCourse(tab, detail);
            }

            if (success) {
                log("      ★ 选课成功！ ★");
//                JOptionPane.showMessageDialog(mainPanel, "抢课成功: " + detail.getJxbmc());
            } else {
                log("      选课失败 (Flag!=1)");
            }
        } catch (Exception e) {
            log("      选课请求异常: " + e.getMessage());
        }
    }

    private int parseInt(String val) {
        if (val == null)
            return 0;
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
