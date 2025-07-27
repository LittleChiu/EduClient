import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class CourseListDemo {

    /* 11 列权重（可按需微调） */
    private static final double[] COL_W = {
            0.15, 0.10, 0.15, 0.10, 0.08,
            0.06, 0.08, 0.08, 0.07, 0.08, 0.05
    };

    private static final int HEADER_H = 40;

    public static void main(String[] args) throws Exception {
        FlatAnimatedLafChange.showSnapshot();
        UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        FlatAnimatedLafChange.hideSnapshotWithAnimation();

        SwingUtilities.invokeLater(CourseListDemo::createAndShowGUI);
    }

    /* ====== 构建窗口 ====== */
    private static void createAndShowGUI() {
        JFrame f = new JFrame("课程列表 — 严格对齐 / 居中 / 换行");
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setLayout(new BorderLayout());

        Box list = Box.createVerticalBox();
        list.setOpaque(false);

        JScrollPane sp = new JScrollPane(list,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(new EmptyBorder(0, 0, 0, 0));
        f.add(sp, BorderLayout.CENTER);

        /* ------------ 示例数据 ------------ */
        list.add(new CoursePanel(sampleCourse1()));
        list.add(new CoursePanel(sampleCourse2()));
        list.add(new CoursePanel(sampleCourse3()));

        list.add(Box.createVerticalGlue());

        f.setSize(1000, 600);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    /* ====== 课程面板（可折叠） ====== */
    static class CoursePanel extends JPanel {
        private final JPanel detail;     // 展开区域（GridBagLayout）
        private final JLabel arrow;      // ▼ / ▲

        CoursePanel(Course course) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);

            /* ---------- Header ---------- */
            JPanel head = new FixedRowPanel(HEADER_H);
            head.setLayout(new GridBagLayout());
            head.setBorder(new MatteBorder(
                    0, 0, 1, 0,
                    UIManager.getColor("Component.borderColor")));
            head.setOpaque(true);
            head.setAlignmentX(LEFT_ALIGNMENT);

            arrow = new JLabel("▼");
            arrow.setFont(arrow.getFont().deriveFont(Font.BOLD, 16f));

            JLabel titleLab = new JLabel(course.codeTitle());
            titleLab.setFont(titleLab.getFont().deriveFont(Font.PLAIN, 14f));

            JLabel creditLab = new JLabel(course.credit() + " 学分");
            creditLab.setFont(creditLab.getFont().deriveFont(Font.PLAIN, 13f));
            creditLab.setForeground(UIManager.getColor("Label.disabledForeground"));

            JLabel countLab = new JLabel("教学班个数：" + course.classCount());
            countLab.setFont(countLab.getFont().deriveFont(Font.PLAIN, 13f));
            countLab.setForeground(UIManager.getColor("Label.disabledForeground"));

            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(0, 8, 0, 8);
            g.gridy = 0; g.anchor = GridBagConstraints.WEST;

            g.gridx = 0; head.add(arrow, g);
            g.gridx++;  head.add(titleLab, g);
            g.gridx++;  head.add(creditLab, g);
            g.gridx++;  head.add(countLab, g);

            g.weightx = 1; g.gridx++;
            head.add(Box.createHorizontalGlue(), g);

            add(head, BorderLayout.NORTH);

            /* ---------- Detail ---------- */
            detail = new JPanel(new GridBagLayout());
            detail.setVisible(false);
            detail.setOpaque(false);
            detail.setAlignmentX(LEFT_ALIGNMENT);

            String[] cols = {"教学班", "上课教师", "上课时间", "教学地点",
                    "开课学院", "选课备注", "课程类别", "课程性质",
                    "教学模式", "已选/容量", "操作"};

            // 表头
            addRow(0, cols, true, null);

            // 数据行 + 斑马纹
            List<TeachingClass> rows = course.classes();
            for (int r = 0; r < rows.size(); r++)
                addRow(r + 1, classToObjects(rows.get(r)),
                        false,
                        r % 2 == 0 ? new Color(250, 250, 250) : null);

            add(detail, BorderLayout.CENTER);

            /* ---------- 点击展开 / 收起 ---------- */
            head.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    boolean show = detail.isVisible();
                    detail.setVisible(!show);
                    arrow.setText(show ? "▼" : "▲");
                    revalidate();
                    repaint();
                }
            });
        }

        /** TeachingClass → Object[]（保持列顺序） */
        private Object[] classToObjects(TeachingClass tc) {
            return new Object[]{
                    tc.id(), tc.teacher(), tc.time(), tc.room(),
                    tc.college(), tc.remark(), tc.courseCategory(),
                    tc.courseNature(), tc.teachMode(),
                    tc.capacity(), tc.action()
            };
        }

        /** 在同一 GridBagLayout 中新增一整行 */
        private void addRow(int rowIdx, Object[] vals,
                            boolean isHeader, Color bg) {

            GridBagConstraints g = new GridBagConstraints();
            g.gridy = rowIdx;
            g.fill   = GridBagConstraints.BOTH;
            g.anchor = GridBagConstraints.CENTER;

            for (int c = 0; c < vals.length; c++) {
                g.gridx    = c;
                g.weightx  = COL_W[c];

                JComponent comp;
                if (!isHeader && c == 10) {
                    JButton btn = new JButton(vals[c].toString());
                    btn.putClientProperty(FlatClientProperties.STYLE, "arc:999");
                    btn.putClientProperty("JButton.buttonType", "toolBarButton");
                    btn.addActionListener(ev ->
                            JOptionPane.showMessageDialog(this,
                                    "点击：" + vals[0]));
                    comp = btn;
                } else {
                    String html = "<html><div style='text-align:center;'>"
                            + vals[c].toString().replace(" ", "&nbsp;")
                            + "</div></html>";
                    JLabel lab = new JLabel(html, SwingConstants.CENTER);
                    lab.setVerticalAlignment(SwingConstants.CENTER);
                    lab.setFont(lab.getFont().deriveFont(
                            isHeader ? Font.BOLD : Font.PLAIN, 12f));
                    comp = lab;
                }
                comp.setOpaque(bg != null);
                if (bg != null) comp.setBackground(bg);

                detail.add(comp, g);
            }
        }
    }

    /* ========== Util：固定高度面板 ========== */
    static class FixedRowPanel extends JPanel {
        private final int h;
        FixedRowPanel(int h) { this.h = h; setOpaque(false); }
        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, h);
        }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
    }

    /* ========== 示例数据（硬编码） ========== */
    private static Course sampleCourse1() {
        return new Course("(13702) 高分子化学及物理实验",
                2.0,
                List.of(new TeachingClass(
                        "(2025‑2026‑1)-19005‑01",
                        "张爱丹 · 副教授",
                        "周二 1‑4 节{9‑16周}",
                        "17‑605",
                        "丝绸", "", "专业课", "实践选修",
                        "其他", "14/21", "选课"
                )));
    }

    private static Course sampleCourse2() {
        TeachingClass c1 = new TeachingClass(
                "(2025‑2026‑1)-14001‑01", "李明 · 教授",
                "周一 5‑6 节{1‑8周}", "12‑310",
                "土木", "", "专业课", "必修",
                "线下", "40/40", "已满");
        TeachingClass c2 = new TeachingClass(
                "(2025‑2026‑1)-14001‑02", "李明 · 教授",
                "周三 3‑4 节{1‑8周}", "12‑310",
                "土木", "", "专业课", "必修",
                "线下", "36/40", "选课");
        // … 其余教学班同理
        return new Course("(14001) 结构力学", 3.0,
                List.of(c1, c2 /*, … */));
    }

    private static Course sampleCourse3() {
        // 省略若干教学班
        return new Course("(15100) 数据结构", 4.0, List.of(
                new TeachingClass(
                        "(2025‑2026‑1)-15100‑01", "王伟 · 副教授",
                        "周三 1‑2 节{1‑16周}", "15‑201",
                        "计算机", "", "专业课", "必修",
                        "线下", "45/60", "选课")
        ));
    }
    /** 教学班（班号 / 任课教师 / 上课时间 …） */
    public record TeachingClass(
            String id,
            String teacher,
            String time,
            String room,
            String college,
            String remark,
            String courseCategory,
            String courseNature,
            String teachMode,
            String capacity,
            String action        // “选课”“已满”…按钮文字
    ) {}

    /** 课程（课程代码 + 名称 + 学分 + 教学班列表） */
    public record Course(
            String codeTitle,   // “(13702) 高分子化学及物理实验”
            double credit,
            List<TeachingClass> classes
    ) {
        /** 教学班个数 */
        public int classCount() { return classes.size(); }
    }

}
