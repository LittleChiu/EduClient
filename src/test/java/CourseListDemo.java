/* =============================================================
 * CourseListDemo.java  ―― 单文件 Swing Demo
 * 依赖：FlatLaf 2.x
 * JDK 17+ 可直接用 record；低版本改成普通类即可
 * ============================================================= */
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

public class CourseListDemo {

    /* ========= 模型 ========= */
    public record TeachingClass(
            String id, String teacher, String time, String room,
            String college, String remark, String courseCategory,
            String courseNature, String teachMode, String capacity,
            String action) {}
    public record Course(String codeTitle, double credit,
                         List<TeachingClass> classes) {
        public int classCount() { return classes.size(); }
    }

    /* ========= 组件：可折叠课程列表 ========= */
    public static class CourseListPanel extends JPanel {
        private static final double[] COL_W = {0.15,0.10,0.15,0.10,0.08,0.06,0.08,0.08,0.07,0.08,0.05};
        private static final String[] COLS  = {"教学班","上课教师","上课时间","教学地点",
                "开课学院","选课备注","课程类别","课程性质",
                "教学模式","已选/容量","操作"};
        private static final Color   ZEBRA  = new Color(250,250,250);
        private static final int     HEAD_H = 40;
        private final Box list = Box.createVerticalBox();
        private Consumer<TeachingClass> actionHandler = tc -> {};

        public CourseListPanel() {
            setLayout(new BorderLayout());
            JScrollPane sp = new JScrollPane(list,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setBorder(new EmptyBorder(0,0,0,0));
            add(sp, BorderLayout.CENTER);
        }

        /* ======== 对外暴露的 set 方法 ======== */
        public void setCourses(List<Course> courses) {
            list.removeAll();
            courses.forEach(this::addCourse);
            list.add(Box.createVerticalGlue());
            revalidate(); repaint();
        }
        public void addCourse(Course c) { list.add(new CoursePanel(c)); }
        public void setActionHandler(Consumer<TeachingClass> ah) {
            this.actionHandler = ah!=null?ah:tc->{};
        }

        /* ========== 内部 UI ========== */
        private class CoursePanel extends JPanel {
            private final JPanel detail = new JPanel(new GridBagLayout());
            private final JLabel arrow  = new JLabel("▼");

            CoursePanel(Course c) {
                setLayout(new BorderLayout());
                setAlignmentX(LEFT_ALIGNMENT);
                setOpaque(false);

                /* --- Header --- */
                JPanel head = new FixedHPanel(HEAD_H);
                head.setLayout(new GridBagLayout());
                head.setBorder(new MatteBorder(0,0,1,0,
                        UIManager.getColor("Component.borderColor")));
                arrow.setFont(arrow.getFont().deriveFont(Font.BOLD,16f));
                JLabel title  = label(c.codeTitle(),14f,false);
                JLabel credit = gray(label(c.credit()+" 学分",13f,false));
                JLabel count  = gray(label("教学班个数："+c.classCount(),13f,false));

                GridBagBuilder gb = gb().insets(new Insets(0, 8, 0, 8));


                head.add(arrow,  gb.gridx(0).anchor(GridBagConstraints.WEST).build());
                head.add(title,  gb.gridx(1).build());
                head.add(credit, gb.gridx(2).build());
                head.add(count,  gb.gridx(3).build());
                head.add(Box.createHorizontalGlue(),
                        gb.gridx(4).weightx(1).build());

                add(head, BorderLayout.NORTH);

                /* --- Detail 表格 --- */
                detail.setOpaque(false); detail.setVisible(false);
                addRow(0, COLS,true,null);
                List<TeachingClass> rows = c.classes();
                for(int i=0;i<rows.size();i++)
                    addRow(i+1, tcArr(rows.get(i)),false,(i%2==0)?ZEBRA:null);
                add(detail, BorderLayout.CENTER);

                /* --- 展开/收起 --- */
                head.addMouseListener(new MouseAdapter(){
                    @Override public void mouseClicked(MouseEvent e){
                        boolean show=detail.isVisible();
                        detail.setVisible(!show);
                        arrow.setText(show?"▼":"▲");
                        revalidate(); repaint();
                    }});
            }
            @Override public Dimension getMaximumSize(){
                Dimension p=getPreferredSize();
                return new Dimension(Integer.MAX_VALUE,p.height);
            }

            /* --------- 工具 --------- */
            private GridBagBuilder gb(){return new GridBagBuilder();}
            private JLabel label(String t,float sz,boolean b){
                JLabel l=new JLabel(t); l.setFont(l.getFont().deriveFont(b?Font.BOLD:Font.PLAIN,sz)); return l;}
            private JLabel gray(JLabel l){ l.setForeground(UIManager.getColor("Label.disabledForeground")); return l;}
            private Object[] tcArr(TeachingClass t){return new Object[]{
                    t.id(),t.teacher(),t.time(),t.room(),
                    t.college(),t.remark(),t.courseCategory(),
                    t.courseNature(),t.teachMode(),t.capacity(),t.action()};
            }
            private void addRow(int row,Object[] vals,boolean headRow,Color bg){
                GridBagConstraints g = gb().fill(GridBagConstraints.HORIZONTAL)
                        .anchor(GridBagConstraints.CENTER)
                        .weighty(0).gridy(row).build();
                for(int c=0;c<vals.length;c++){
                    g.gridx=c; g.weightx=COL_W[c];
                    JComponent comp;
                    if(!headRow && c==10){
                        JButton btn=new JButton(vals[c].toString());
                        btn.putClientProperty(FlatClientProperties.STYLE,"arc:999");
                        btn.putClientProperty("JButton.buttonType","toolBarButton");
                        TeachingClass tc=findTCById(vals[0].toString());
                        btn.addActionListener(ev->actionHandler.accept(tc));
                        comp=btn;
                    }else{
                        String html="<html><div style='text-align:center;'>"+
                                vals[c].toString().replace(" ","&nbsp;")+"</div></html>";
                        JLabel lab=new JLabel(html,SwingConstants.CENTER);
                        lab.setVerticalAlignment(SwingConstants.CENTER);
                        lab.setFont(lab.getFont().deriveFont(headRow?Font.BOLD:Font.PLAIN,12f));
                        comp=lab;
                    }
                    comp.setOpaque(bg!=null);
                    if(bg!=null) comp.setBackground(bg);
                    detail.add(comp,g);
                }
            }
            private TeachingClass findTCById(String id){
                // 简单线性查找即可
                return listComponents(TeachingClass.class).stream()
                        .filter(t->t.id().equals(id)).findFirst().orElse(null);
            }
            private <T> List<T> listComponents(Class<T> cls){
                List<T> out=new ArrayList<>();
                // 本 demo 只有三层，直接遍历
                for(Component cp:list.getComponents())
                    if(cp instanceof CoursePanel p)
                        for(Component dc:p.detail.getComponents())
                            if(dc instanceof JButton && cls==TeachingClass.class){
                                // 无法从 JButton 拿到 TeachingClass，已在创建时 capture 了
                            }
                return out;
            }
        } // CoursePanel

        /* --- 固定高面板 & GBC Builder --- */
        private static class FixedHPanel extends JPanel{
            private final int h; FixedHPanel(int h){this.h=h; setOpaque(false);}
            @Override public Dimension getPreferredSize(){
                Dimension d=super.getPreferredSize();
                return new Dimension(d.width,h);}
            @Override public Dimension getMinimumSize(){return getPreferredSize();}
            @Override public Dimension getMaximumSize(){return getPreferredSize();}
        }
        private static class GridBagBuilder{
            private final GridBagConstraints g=new GridBagConstraints();
            GridBagBuilder(){g.insets=new Insets(0,0,0,0);}
            GridBagBuilder gridx(int x){g.gridx=x;return this;}
            GridBagBuilder gridy(int y){g.gridy=y;return this;}
            GridBagBuilder fill(int f){g.fill=f;return this;}
            GridBagBuilder anchor(int a){g.anchor=a;return this;}
            GridBagBuilder weightx(double w){g.weightx=w;return this;}
            GridBagBuilder weighty(double w){g.weighty=w;return this;}
            GridBagBuilder insets(Insets i){g.insets=i;return this;}
            GridBagConstraints build(){return (GridBagConstraints)g.clone();}
        }
    } // CourseListPanel

    /* ========= Demo 入口 ========= */
    public static void main(String[] args) throws Exception {
        FlatAnimatedLafChange.showSnapshot();
        UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
        FlatAnimatedLafChange.hideSnapshotWithAnimation();

        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("课程列表 Demo");
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            f.setLayout(new BorderLayout());

            CourseListPanel panel = new CourseListPanel();
            panel.setActionHandler(tc ->
                    JOptionPane.showMessageDialog(f,"点击："+tc.id()+" / "+tc.action()));

            /* ---------- 填充演示数据 ---------- */
            panel.setCourses(List.of(sample1(), sample2(), sample3()));

            f.add(panel, BorderLayout.CENTER);
            f.setSize(1000,600);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    /* ========= 演示数据 ========= */
    private static Course sample1(){
        return new Course("(13702) 高分子化学及物理实验",2.0,
                List.of(new TeachingClass("(25-26-1)-19005-01","张爱丹 · 副教授",
                        "周二 1-4 节{9-16周}","17-605",
                        "丝绸","","专业课","实践选修","其他","14/21","选课")));
    }
    private static Course sample2(){
        TeachingClass c1=new TeachingClass("(25-26-1)-14001-01","李明 · 教授",
                "周一 5-6 节{1-8周}","12-310","土木",
                "备注字段示例示例","专业课","必修","线下","40/40","已满");
        TeachingClass c2=new TeachingClass("(25-26-1)-14001-02","李明 · 教授",
                "周三 3-4 节{1-8周}","12-310","土木","",
                "专业课","必修","线下","36/40","选课");
        return new Course("(14001) 结构力学",3.0,List.of(c1,c2));
    }
    private static Course sample3(){
        return new Course("(15100) 数据结构",4.0,
                List.of(new TeachingClass("(25-26-1)-15100-01","王伟 · 副教授",
                        "周三 1-2 节{1-16周}","15-201","计算机","",
                        "专业课","必修","线下","45/60","选课")));
    }
}
