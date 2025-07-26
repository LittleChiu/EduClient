package me.yeoc.educlient.gui;

import lombok.Getter;

import javax.swing.*;

public class UserInfoPanel {
    @Getter
    private JPanel mainPanel;
    private JPanel authPanel;
    private JTextField sessionIdField;
    private JTextField routeField;
    private JButton authButtom;


    public UserInfoPanel(){
        init();
    }

    private void init(){
        authButtom.addActionListener(e->{
            System.out.println("a");
        });
    }

}
