package me.yeoc.educlient.gui;

import lombok.Getter;
import me.yeoc.educlient.object.CourseItem;
import me.yeoc.educlient.object.CourseTab;
import me.yeoc.educlient.service.EduService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class CourseTabGUI {
    private final MainGUI mainGUI;
    private final CourseTab courseTab;

    @Getter
    private JPanel mainPanel;

    private JTextField keywordField;
    private JButton searchButton;
    private JButton enrollButton;

    private JTable masterTable;
    private DefaultTableModel masterModel;

    private JTable detailTable;
    private DefaultTableModel detailModel;

    // Columns
    private static final String[] MASTER_COLUMNS = {
            "课程名称 (kcmc)",
            "课程代码 (kch)",
            "学分 (xf)"
    };

    private static final String[] DETAIL_COLUMNS = {
            "任课老师",
            "教学班名称 (jxbmc)",
            "上课时间 (sksj)",
            "已选",
            "容量"
    };

    public CourseTabGUI(MainGUI mainGUI, CourseTab courseTab) {
        this.mainGUI = mainGUI;
        this.courseTab = courseTab;
        initUI();
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("关键字:"));
        keywordField = new JTextField(20);
        topPanel.add(keywordField);
        searchButton = new JButton("查询课程");
        topPanel.add(searchButton);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Center: Split Pane (Master-Detail)
        initTables();
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(masterTable), new JScrollPane(detailTable));
        splitPane.setDividerLocation(200);
        splitPane.setResizeWeight(0.5);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Bottom: Enroll
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        enrollButton = new JButton("抢课/选课");
        bottomPanel.add(enrollButton);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Listeners
        searchButton.addActionListener(e -> doSearch());
        enrollButton.addActionListener(e -> doEnroll());
        keywordField.addActionListener(e -> doSearch());

        // Master Table Selection Listener
        masterTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onMasterSelectionChanged();
            }
        });
    }

    private void initTables() {
        // Master Table
        masterModel = new DefaultTableModel(MASTER_COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        masterTable = new JTable(masterModel);
        masterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Detail Table
        detailModel = new DefaultTableModel(DETAIL_COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        detailTable = new JTable(detailModel);
        detailTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    private void doSearch() {
        String keyword = keywordField.getText().trim();
        EduService service = mainGUI.getEduService();
        if (service == null) {
            JOptionPane.showMessageDialog(mainPanel, "请先在个人信息页登录！");
            return;
        }

        searchButton.setEnabled(false);
        detailModel.setRowCount(0); // Clear details

        new Thread(() -> {
            try {
                mainGUI.info("正在查询: " + courseTab.getName() + " -> " + keyword);

                if (courseTab.getBklxId() == null) {
                    service.fetchCourseTabMetadata(courseTab);
                }

                List<CourseItem> fullList = service.fetchCourseList(courseTab, keyword);

                // Deduplicate for Master View (by kchId) but keep full list for context
                List<CourseItem> uniqueList = new ArrayList<>();
                Set<String> seenKchIds = new HashSet<>();
                // Group items by kch_id for later context retrieval
                Map<String, List<CourseItem>> contextMap = new HashMap<>();

                for (CourseItem item : fullList) {
                    contextMap.computeIfAbsent(item.getKchId(), k -> new ArrayList<>()).add(item);

                    if (item.getKchId() != null && !seenKchIds.contains(item.getKchId())) {
                        seenKchIds.add(item.getKchId());
                        uniqueList.add(item);
                    }
                }

                masterTable.putClientProperty("contextMap", contextMap);

                SwingUtilities.invokeLater(() -> {
                    masterModel.setRowCount(0);
                    for (CourseItem item : uniqueList) {
                        Vector<Object> row = new Vector<>();
                        row.add(item.getKch());
                        row.add(item.getKcmc());
                        row.add(item.getXf());
                        row.add(item.getKklxdm()); // Need mapping to name?
                        masterModel.addRow(row);
                    }
                    masterTable.putClientProperty("masterList", uniqueList);
                    mainGUI.info("查询完成，共找到 " + uniqueList.size() + " 门课程");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    mainGUI.info("查询失败: " + ex.getMessage());
                    JOptionPane.showMessageDialog(mainPanel, "查询失败: " + ex.getMessage());
                });
            } finally {
                SwingUtilities.invokeLater(() -> searchButton.setEnabled(true));
            }
        }).start();
    }

    private void onMasterSelectionChanged() {
        int selectedRow = masterTable.getSelectedRow();
        if (selectedRow == -1) {
            detailModel.setRowCount(0);
            return;
        }

        @SuppressWarnings("unchecked")
        List<CourseItem> masterList = (List<CourseItem>) masterTable.getClientProperty("masterList");
        if (masterList == null || selectedRow >= masterList.size())
            return;

        CourseItem selectedCourse = masterList.get(selectedRow);
        System.out.println(selectedCourse);
        loadDetails(selectedCourse);
    }

    private void loadDetails(CourseItem course) {
        EduService service = mainGUI.getEduService();
        if (service == null)
            return;

        detailModel.setRowCount(0);
        mainGUI.info("正在加载教学班信息: " + course.getKcmc());

        new Thread(() -> {
            try {
                String keyword = keywordField.getText().trim();
                @SuppressWarnings("unchecked")
                Map<String, List<CourseItem>> contextMap = (Map<String, List<CourseItem>>) masterTable
                        .getClientProperty("contextMap");
                List<CourseItem> contextItems = Collections.emptyList();
                if (contextMap != null) {
                    contextItems = contextMap.getOrDefault(course.getKchId(), Collections.emptyList());
                }

                List<CourseItem> details = service.fetchCourseDetails(courseTab, keyword, course.getKchId(),
                        contextItems);

                SwingUtilities.invokeLater(() -> {
                    detailModel.setRowCount(0);
                    for (CourseItem item : details) {
                        Vector<Object> row = new Vector<>();
                        row.add(item.getTeacherName());
                        row.add(item.getJxbmc());
                        row.add(item.getSksj());
                        row.add(item.getYxzrs());
                        row.add(item.getJxbrl());
                        detailModel.addRow(row);
                    }
                    detailTable.putClientProperty("detailList", details);
                    mainGUI.info("加载完成，共找到 " + details.size() + " 个教学班");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> mainGUI.info("加载教学班失败: " + ex.getMessage()));
            }
        }).start();
    }

    @SuppressWarnings("unchecked")
    private void doEnroll() {
        int selectedRow = detailTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(mainPanel, "请先在下方列表选择一个教学班！");
            return;
        }

        List<CourseItem> detailList = (List<CourseItem>) detailTable.getClientProperty("detailList");
        if (detailList == null || selectedRow >= detailList.size())
            return;

        CourseItem course = detailList.get(selectedRow);

        int confirm = JOptionPane.showConfirmDialog(mainPanel,
                "确认要选这门课吗？\n" + course.getKcmc() + "\n" + course.getJxbmc() + "\n老师: " + course.getTeacherName(),
                "确认选课", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION)
            return;

        EduService service = mainGUI.getEduService();
        enrollButton.setEnabled(false);

        new Thread(() -> {
            try {
                mainGUI.info("正在选课: " + course.getKcmc());
                String jxbIds = service.getSmartJxbIds(courseTab, course);
                boolean success;
                if (jxbIds != null) {
                    mainGUI.info("检测到关联子课程，尝试合并选课...");
                    success = service.enrollCourse(courseTab, course, jxbIds);
                } else {
                    success = service.enrollCourse(courseTab, course);
                }
                SwingUtilities.invokeLater(() -> {
                    if (success) {
                        mainGUI.info("选课成功: " + course.getKcmc());
//                        JOptionPane.showMessageDialog(mainPanel, "选课成功！\n" + course.getKcmc());
                    } else {
                        mainGUI.info("选课失败: " + course.getKcmc());
//                        JOptionPane.showMessageDialog(mainPanel, "选课失败，请查看日志或稍后重试。");
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    mainGUI.info("选课失败: " + ex.getMessage());
                    JOptionPane.showMessageDialog(mainPanel, "选课请求失败: " + ex.getMessage());
                });
            } finally {
                SwingUtilities.invokeLater(() -> enrollButton.setEnabled(true));
            }
        }).start();
    }
}
