package me.yeoc.educlient.gui;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import me.yeoc.educlient.service.EduService;

import javax.swing.*;

public class MainGUI {
    private JPanel mainPanel;
    @Getter @Setter
    private EduService eduService;
    private JTabbedPane tabbedPane;
    private JTextArea logArea;


    @SneakyThrows
    public static void start() {
        UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
//        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        JFrame frame = new JFrame("教务管理系统 学生客户端 ver.1.0 by LittleQiu233");
        frame.setContentPane(new MainGUI().mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        frame.pack();
        frame.setSize(1280, 600);
        frame.setVisible(true);

    }

    public MainGUI(){
        tabbedPane.addTab("选课管理",new ChooseCourseGUI(this).getMainPanel());

        tabbedPane.addTab("个人信息",new UserInfoPanel(this).getMainPanel());
    }

    public void info(String str) {
        logArea.append(str + "\n");
//        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}
