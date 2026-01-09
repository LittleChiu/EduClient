package me.yeoc.educlient.gui;

import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import me.yeoc.educlient.service.EduService;
import me.yeoc.educlient.util.ConfigManager;

import javax.swing.*;
import java.awt.*;

public class UserInfoPanel {
    private final MainGUI mainGUI;
    @Getter
    private JPanel mainPanel;

    // Auth Fields
    private JTextField sessionIdField;
    private JTextField routeField;
    private JCheckBox rememberMeBox;
    private JButton authButton;

    public UserInfoPanel(MainGUI mainGUI) {
        this.mainGUI = mainGUI;
        initUI();
    }

    private void initUI() {
        mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Title
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel titleLabel = new JLabel("登录验证 (Cookie Login)");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(titleLabel, gbc);

        // Session ID
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 1;
        mainPanel.add(new JLabel("JSESSIONID:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        sessionIdField = new JTextField(30);
        mainPanel.add(sessionIdField, gbc);

        // Route
        gbc.gridx = 0;
        gbc.gridy = 2;
        mainPanel.add(new JLabel("route:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        routeField = new JTextField(30);
        mainPanel.add(routeField, gbc);

        // Remember Me
        gbc.gridx = 1;
        gbc.gridy = 3;
        rememberMeBox = new JCheckBox("记住我 (Remember Me)");
        mainPanel.add(rememberMeBox, gbc);

        // Login Button
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        authButton = new JButton("校验 / 登录");
        mainPanel.add(authButton, gbc);

        // Spacer to push everything up
        gbc.gridy = 5;
        gbc.weighty = 1.0;
        mainPanel.add(new JPanel(), gbc);

        // Logic
        authButton.addActionListener(e -> doLogin());

        loadConfig();
    }

    private void loadConfig() {
        JSONObject config = ConfigManager.loadConfig();
        if (config != null && config.getBooleanValue("remember")) {
            sessionIdField.setText(config.getString("sessionId"));
            routeField.setText(config.getString("route"));
            rememberMeBox.setSelected(true);
//            mainGUI.info("已自动填充保存的 Cookie 信息。");
        }
    }

    private void doLogin() {
        String sessionId = sessionIdField.getText().trim();
        String route = routeField.getText().trim();

        if (sessionId.isEmpty() || route.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "请把 Cookie 填完！");
            return;
        }

        EduService eduService = new EduService();
        try {
            eduService.setCookie("JSESSIONID=" + sessionId + "; route=" + route);
            mainGUI.setEduService(eduService);

            ConfigManager.saveConfig(sessionId, route, rememberMeBox.isSelected());

            mainGUI.info(
                    "设置 Cookie 成功! (JSESSIONID=..." + sessionId.substring(Math.max(0, sessionId.length() - 6)) + ")");
            JOptionPane.showMessageDialog(mainPanel, "登录信息已设置。");
        } catch (Exception ex) {
            mainGUI.info("设置失败: " + ex.getMessage());
        }
    }
}
