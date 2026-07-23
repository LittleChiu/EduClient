package me.yeoc.educlient.gui;

import me.yeoc.educlient.object.CourseItem;
import me.yeoc.educlient.object.CourseTab;
import me.yeoc.educlient.service.EduService;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BatchSnatchPanel {
    private final MainGUI mainGUI;
    private JPanel mainPanel;
    private JTextArea inputArea;
    private JTextArea logArea;
    private JButton startButton;
    private JButton stopButton;
    private JCheckBox loopCheckBox;
    private JSpinner requestTimeoutSpinner;
    private JSpinner threadCountSpinner;
    private JSpinner durationSpinner;
    private JSpinner intervalSpinner;
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
        loopCheckBox = new JCheckBox("循环模式");
        requestTimeoutSpinner = new JSpinner(new SpinnerNumberModel(1500, 200, 10000, 100));
        threadCountSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 32, 1));
        durationSpinner = new JSpinner(new SpinnerNumberModel(15, 1, 600, 1));
        intervalSpinner = new JSpinner(new SpinnerNumberModel(80, 0, 2000, 10));
        topPanel.add(startButton);
        topPanel.add(stopButton);
        topPanel.add(new JLabel("单次超时(ms):"));
        topPanel.add(requestTimeoutSpinner);
        topPanel.add(new JLabel("并发线程:"));
        topPanel.add(threadCountSpinner);
        topPanel.add(new JLabel("持续抢课(s):"));
        topPanel.add(durationSpinner);
        topPanel.add(new JLabel("线程间隔(ms):"));
        topPanel.add(intervalSpinner);
        topPanel.add(loopCheckBox);
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
        int requestTimeoutMs = getIntValue(requestTimeoutSpinner);
        int threadCount = getIntValue(threadCountSpinner);
        int durationSeconds = getIntValue(durationSpinner);
        int intervalMs = getIntValue(intervalSpinner);
        log("=== 开始批量抢课任务，共 " + ids.length + " 个目标 ===");
        log("参数：单次超时=" + requestTimeoutMs + "ms，并发线程=" + threadCount + "，持续抢课=" + durationSeconds
                + "s，线程间隔=" + intervalMs + "ms");

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

                    processSingleId(service, tabs, id, requestTimeoutMs, threadCount, durationSeconds, intervalMs);

                    // Small delay to be nice
                    // Thread.sleep(500);
                }
                log("=== 批量任务结束 ===");
            } catch (Exception e) {
                log("任务异常终止: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // If running is still true, it means we finished naturally (not stopped by
                // user)
                boolean shouldLoop = running.get() && loopCheckBox.isSelected();
                running.set(false);
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    if (shouldLoop) {
                        log(">>> 循环模式开启，自动重新开始本次任务...");
                        startButton.doClick();
                    }
                });
            }
        }).start();
    }

    private void processSingleId(EduService service, List<CourseTab> tabs, String keyword, int requestTimeoutMs,
            int threadCount, int durationSeconds, int intervalMs) {
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
                    processCourseHead(service, tab, keyword, courseHead, requestTimeoutMs, threadCount,
                            durationSeconds, intervalMs);
                }

            } catch (Exception e) {
                log("  [类别: " + tab.getName() + "] 查询出错: " + e.getMessage() + " (已跳过)");
            }
        }

        if (!foundAny) {
            log("未在任何类别中找到匹配课程: " + keyword);
        }
    }

    private void processCourseHead(EduService service, CourseTab tab, String keyword, CourseItem courseHead,
            int requestTimeoutMs, int threadCount, int durationSeconds, int intervalMs) {
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
                attemptEnroll(service, tab, detail, jxbIds, requestTimeoutMs, threadCount, durationSeconds,
                        intervalMs);
                if (!running.get()) {
                    return;
                }
            }

        } catch (Exception e) {
            log("    处理课程 [" + courseHead.getKcmc() + "] 详情时出错: " + e.getMessage());
        }
    }

    private void attemptEnroll(EduService service, CourseTab tab, CourseItem detail, String overrideJxbIds,
            int requestTimeoutMs, int threadCount, int durationSeconds, int intervalMs) {
        long deadline = System.currentTimeMillis() + durationSeconds * 1000L;
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicInteger timeouts = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        log("      开始并发抢课: " + detail.getJxbmc() + " | 持续 " + durationSeconds + "s | " + threadCount + " 线程");

        for (int i = 0; i < threadCount; i++) {
            final int workerIndex = i + 1;
            pool.submit(() -> {
                while (running.get() && !success.get() && System.currentTimeMillis() < deadline) {
                    try {
                        boolean currentSuccess;
                        if (overrideJxbIds != null) {
                            currentSuccess = service.enrollCourse(tab, detail, overrideJxbIds, requestTimeoutMs);
                        } else {
                            currentSuccess = service.enrollCourse(tab, detail, requestTimeoutMs);
                        }
                        int currentAttempt = attempts.incrementAndGet();
                        if (currentSuccess) {
                            if (success.compareAndSet(false, true)) {
                                log("      ★ 选课成功！线程#" + workerIndex + "，总请求次数=" + currentAttempt);
                            }
                            break;
                        }
                        if (currentAttempt == 1 || currentAttempt % 10 == 0) {
                            log("      进行中: 已发起 " + currentAttempt + " 次请求，尚未成功");
                        }
                    } catch (Exception e) {
                        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        if (message.contains("超时")) {
                            int currentTimeouts = timeouts.incrementAndGet();
                            if (currentTimeouts == 1 || currentTimeouts % 10 == 0) {
                                log("      超时累计 " + currentTimeouts + " 次，继续并发重试...");
                            }
                        } else {
                            int currentFailures = failures.incrementAndGet();
                            if (currentFailures <= 3 || currentFailures % 10 == 0) {
                                log("      请求异常(" + currentFailures + "): " + message);
                            }
                        }
                    }

                    if (!success.get() && running.get() && intervalMs > 0) {
                        try {
                            Thread.sleep(intervalMs + ThreadLocalRandom.current().nextInt(0, 31));
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            });
        }

        pool.shutdown();
        try {
            long waitSeconds = Math.max(1, durationSeconds + 5L);
            pool.awaitTermination(waitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            if (!pool.isTerminated()) {
                pool.shutdownNow();
            }
        }

        if (!success.get() && running.get()) {
            log("      本轮结束：未抢到，累计请求 " + attempts.get() + " 次，超时 " + timeouts.get() + " 次，异常 "
                    + failures.get() + " 次");
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

    private int getIntValue(JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }
}
