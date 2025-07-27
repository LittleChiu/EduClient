package me.yeoc.educlient.gui;

import lombok.Getter;
import lombok.NonNull;
import me.yeoc.educlient.service.EduService;

import javax.swing.*;

public class UserInfoPanel {
    MainGUI mainGUI;
    @Getter
    private JPanel mainPanel;
    private JPanel authPanel;
    private JTextField sessionIdField;
    private JTextField routeField;
    private JButton authButtom;
    private JTextField textField1;
    private JTextField textField2;
    private JTextField textField3;
    private JTextField textField4;


    public UserInfoPanel(MainGUI mainGUI){
        this.mainGUI = mainGUI;
        init();
    }

    private void init(){
        authButtom.addActionListener(e->{
            EduService eduService = new EduService();
            eduService.setCookie("JSESSIONID="+sessionIdField.getText()+"; route="+routeField.getText());
            mainGUI.setEduService(eduService);
            mainGUI.info("设置Cookie成功! cookies:"+eduService.getCookie());
        });
    }

}
