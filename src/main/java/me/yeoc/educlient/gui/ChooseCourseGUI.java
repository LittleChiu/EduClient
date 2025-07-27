package me.yeoc.educlient.gui;

import lombok.Getter;
import me.yeoc.educlient.object.CourseTab;

import javax.swing.*;
import java.io.IOException;

public class ChooseCourseGUI {
    private MainGUI mainGUI;

    private JTabbedPane courseTabPane;
    private JButton updateButton;
    private JTextArea textArea1;
    private JButton button2;
    @Getter
    private JPanel mainPanel;


    public ChooseCourseGUI(MainGUI mainGUI){
        this.mainGUI = mainGUI;
        init();
    }


    private void init(){
        updateButton.addActionListener(e -> {
            if (mainGUI.getEduService() == null){
                mainGUI.info("请先登录!");
                return;
            }
            try {
                courseTabPane.removeAll();
                for (CourseTab courseTab : mainGUI.getEduService().fetchCourseTabs()) {
                    System.out.println(courseTab);
                    courseTabPane.addTab(courseTab.getName(),new JPanel());
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

}
