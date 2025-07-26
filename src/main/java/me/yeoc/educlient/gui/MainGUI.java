package me.yeoc.educlient.gui;

import lombok.SneakyThrows;

import javax.swing.*;

public class MainGUI {
    private JPanel mainPanel;
    private JTabbedPane tabbedPane;


    @SneakyThrows
    public static void start() {
        UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
//        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        JFrame frame = new JFrame("教务管理系统 学生客户端 ver.1.0 by LittleQiu233");
        frame.setContentPane(new MainGUI().mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

    }

    public MainGUI(){
        tabbedPane.addTab("个人信息",new UserInfoPanel().getMainPanel());
    }
}
