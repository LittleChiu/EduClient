package me.yeoc.educlient.gui;

import lombok.Getter;
import me.yeoc.educlient.service.GradeAnalyzeService;

import javax.swing.*;

public class GradeQueryUI {
    private MainGUI mainGUI;
    private GradeAnalyzeService service;

    @Getter
    private JPanel mainPanel;
    private JTable table1;
    private JTextField textField1;
    private JTextField textField2;
    private JTextField textField3;
    private JComboBox comboBox1;
    private JComboBox comboBox2;
    private JButton 查询Button;
    private JButton 导出Button;

    public GradeQueryUI(MainGUI mainGUI){
        this.mainGUI = mainGUI;
        init();
    }

    private void init(){
        service = new GradeAnalyzeService();


    }


}
