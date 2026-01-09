package me.yeoc.educlient.gui;

import me.yeoc.educlient.object.CourseItem;
import me.yeoc.educlient.service.EduService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

public class MonitorPanel {
    private final MainGUI mainGUI;
    private JPanel mainPanel;
    private JTable monitorTable;
    private DefaultTableModel tableModel;
    private JButton refreshButton;

    private static final String[] COLUMNS = {
            "课程名称",
            "任课教师",
            "上课时间",
            "教学班容量",
            "已选人数",
            "状态"
    };

    public MonitorPanel(MainGUI mainGUI) {
        this.mainGUI = mainGUI;
        initUI();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout());

        // Top Toolbar
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        refreshButton = new JButton("刷新数据");
        topPanel.add(refreshButton);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        // Table
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        monitorTable = new JTable(tableModel);

        // Custom Renderer for Status Column (Red text for overflow)
        monitorTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null) {
                    String status = value.toString();
                    if (status.contains("爆满") || status.contains("超员")) {
                        setForeground(Color.RED);
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else {
                        setForeground(Color.BLACK); // Reset
                    }
                }
                return c;
            }
        });

        mainPanel.add(new JScrollPane(monitorTable), BorderLayout.CENTER);

        // Listeners
        refreshButton.addActionListener(e -> refreshData());
    }

    private void refreshData() {
        EduService service = mainGUI.getEduService();
        if (service == null) {
            JOptionPane.showMessageDialog(mainPanel, "请先登录！");
            return;
        }

        refreshButton.setEnabled(false);
        tableModel.setRowCount(0);

        new Thread(() -> {
            try {
                List<CourseItem> enrolledCourses = service.fetchEnrolledCourses();
                SwingUtilities.invokeLater(() -> {
                    for (CourseItem item : enrolledCourses) {
                        Vector<Object> row = new Vector<>();
                        System.out.println(item.getJxbmc());
                        row.add(item.getKcmc());
                        row.add(item.getTeacherName()); // Assuming verify CourseItem has jsxx parsing
                        row.add(item.getSksj());

                        // Capacity parsing
                        int capacity = parseInt(item.getJxbrs());
                        int enrolled = parseInt(item.getYxzrs());

                        row.add(capacity);
                        row.add(enrolled);

                        // Status Logic
                        if (enrolled > capacity) {
                            row.add("⚠ 超员 " + (enrolled - capacity));
                        } else if (enrolled == capacity) {
                            row.add("已满");
                        } else {
                            row.add("有余量 (" + (capacity - enrolled) + ")");
                        }

                        tableModel.addRow(row);
                    }
                    mainGUI.info("已选课程刷新成功，共 " + enrolledCourses.size() + " 门");
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    mainGUI.info("刷新失败: " + e.getMessage());
                    JOptionPane.showMessageDialog(mainPanel, "获取数据失败: " + e.getMessage());
                });
            } finally {
                SwingUtilities.invokeLater(() -> refreshButton.setEnabled(true));
            }
        }).start();
    }

    private int parseInt(String val) {
        if (val == null)
            return 0;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
