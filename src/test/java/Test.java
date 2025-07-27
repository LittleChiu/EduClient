import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

public class Test {
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf"); } catch (Exception ignored) {}

        JFrame frame = new JFrame("选课系统 - 课程展示");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(new EmptyBorder(6, 6, 6, 6));

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setPreferredSize(new Dimension(820, 450));
        frame.add(scroll);

        /* mock 数据 */
        for (int i = 1; i <= 5; i++) {
            String title = "(1370" + i + ") 高分子化学及物理实验 - 2.0 学分  教学班个数：1";
            List<String[]> rows = Arrays.asList(new String[][]{
                    {"(2025-2026-1)-19005-0" + i, "张爱丹 副教授", "星期二第1‑4节{9‑16周}",
                            "17‑605", "丝绸", "", "专业课", "实践选修", "其他", "14/21", "选课"}
            });
            listPanel.add(new CoursePanel(title, rows));
            listPanel.add(Box.createVerticalStrut(3));
        }

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

/* ───────────── 可展开课程卡片 ───────────── */
class CoursePanel extends JPanel {
    private boolean expanded = false;
    private final JPanel detailPanel;
    private final JButton toggleBtn;

    /* 列定义 & 宽度 */
    private static final String[] COLS = {"教学班","上课教师","上课时间","教学地点","开课学院",
            "选课备注","课程类别","课程性质","教学模式","已选/容量","操作"};
    private static final int[]   COL_W = {130,90,150,80,70,60,70,70,70,70,60};

    /* 行高 = 字体高 + padding */
    private static final int ROW_H;
    static {
        Font f = UIManager.getFont("Label.font");
        FontMetrics fm = Toolkit.getDefaultToolkit().getFontMetrics(f);
        ROW_H = fm.getAscent() + fm.getDescent() + 4;   // +4 做微小上下留白
    }

    public CoursePanel(String title, List<String[]> tableRows) {
        setLayout(new BorderLayout());
        setBackground(new Color(250,250,250));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220,220,220)),
                new EmptyBorder(4,8,4,8)
        ));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        /* 顶部：标题 + 展开按钮 */
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(titleLbl.getFont().deriveFont(Font.BOLD, 13f));
        toggleBtn = new JButton("展开");
        slimBtn(toggleBtn);                       // 极简按钮
        top.add(titleLbl, BorderLayout.WEST);
        top.add(toggleBtn, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        /* 明细表 */
        detailPanel = new JPanel(new BorderLayout());
        detailPanel.setVisible(false);

        JPanel table = new JPanel();
        table.setLayout(new BoxLayout(table, BoxLayout.Y_AXIS));
        table.setOpaque(false);

        table.add(buildRow(COLS, true));
        for (String[] r : tableRows) table.add(buildRow(r, false));

        detailPanel.add(table, BorderLayout.CENTER);
        add(detailPanel, BorderLayout.CENTER);

        toggleBtn.addActionListener(e -> toggle());
    }

    /* 构造表头 / 数据行 */
    private JPanel buildRow(String[] data, boolean isHeader){
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        row.setBackground(isHeader ? new Color(240,240,240) : Color.WHITE);
        lockSize(row, new Dimension(0, ROW_H));            // 行本身锁高

        for(int i=0;i<data.length;i++){
            if(i==data.length-1 && !isHeader){
                JButton btn = new JButton(data[i]);
                slimBtn(btn);
                lockSize(btn, new Dimension(COL_W[i], ROW_H));
                btn.addActionListener(e -> JOptionPane.showMessageDialog(
                        this,"你点击了【选课】"));
                row.add(btn);
            }else{
                JLabel lab = new JLabel(data[i], SwingConstants.CENTER);
                if(isHeader) lab.setFont(lab.getFont().deriveFont(Font.BOLD));
                lockSize(lab, new Dimension(COL_W[i], ROW_H));
                row.add(lab);
            }
        }
        return row;
    }

    /* Button 样式瘦身：去边距、去焦点圈 */
    private static void slimBtn(AbstractButton b){
        b.setMargin(new Insets(0,0,0,0));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setFont(UIManager.getFont("Label.font"));   // 与标签同字号
    }

    /* 一次性锁定首选/最小/最大尺寸 */
    private static void lockSize(JComponent c, Dimension d){
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
    }

    /* 展开/收起 */
    private void toggle(){
        expanded = !expanded;
        detailPanel.setVisible(expanded);
        toggleBtn.setText(expanded ? "收起" : "展开");
        revalidate();
        repaint();
    }
}
