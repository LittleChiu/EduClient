package me.yeoc.educlient;

import me.yeoc.educlient.gui.MainGUI;

public class App {
    public static void main(String[] args) {
        System.out.println("Hello World!");
        try {
            MainGUI.start();
        } catch (Exception e) {
            e.printStackTrace();
            javax.swing.JOptionPane.showMessageDialog(null, "启动失败: " + e.getMessage());
        }
    }
}
