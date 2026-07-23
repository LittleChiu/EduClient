package me.yeoc.educlient.gui;

import javafx.embed.swing.JFXPanel;
import lombok.Getter;
import me.yeoc.educlient.service.EduService;
import me.yeoc.educlient.service.EmbeddedLoginService;

import javax.swing.*;
import java.awt.*;

public class UserInfoPanel {
    private final MainGUI mainGUI;
    private final EmbeddedLoginService loginService = new EmbeddedLoginService();

    @Getter
    private JPanel mainPanel;
    private JLabel statusLabel;
    private JLabel locationLabel;
    private JButton restartButton;

    public UserInfoPanel(MainGUI mainGUI) {
        this.mainGUI = mainGUI;
        initUI();
    }

    private void initUI() {
        mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel header = new JPanel(new BorderLayout(8, 4));
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        statusLabel = new JLabel("请在下方完成统一身份认证登录");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 15f));
        locationLabel = new JLabel(EmbeddedLoginService.LOGIN_URL);
        locationLabel.setForeground(Color.GRAY);
        textPanel.add(statusLabel);
        textPanel.add(locationLabel);
        header.add(textPanel, BorderLayout.CENTER);

        restartButton = new JButton("重新登录");
        restartButton.addActionListener(event -> {
            mainGUI.setEduService(null);
            statusLabel.setText("请在下方完成统一身份认证登录");
            loginService.reloadLogin();
        });
        header.add(restartButton, BorderLayout.EAST);
        mainPanel.add(header, BorderLayout.NORTH);

        JFXPanel browserPanel = new JFXPanel();
        browserPanel.setPreferredSize(new Dimension(1000, 580));
        browserPanel.setBorder(BorderFactory.createLineBorder(new Color(210, 210, 210)));
        mainPanel.add(browserPanel, BorderLayout.CENTER);

        loginService.initialize(browserPanel,
                location -> SwingUtilities.invokeLater(() -> locationLabel.setText(shorten(location))),
                cookie -> SwingUtilities.invokeLater(() -> finishLogin(cookie)),
                error -> SwingUtilities.invokeLater(() -> showError(error)));
    }

    private void finishLogin(String cookie) {
        statusLabel.setText("正在校验教务系统会话...");
        restartButton.setEnabled(false);
        new Thread(() -> {
            EduService service = new EduService();
            service.setCookie(cookie);
            try {
                if (!service.validateSession()) {
                    throw new IllegalStateException("教务系统未确认登录成功，请重新登录。");
                }
                SwingUtilities.invokeLater(() -> {
                    mainGUI.setEduService(service);
                    statusLabel.setText("登录成功，Cookie 已自动获取");
                    statusLabel.setForeground(new Color(25, 125, 55));
                    restartButton.setEnabled(true);
                    mainGUI.info("统一身份认证登录成功，会话 Cookie 已自动设置。");
                    JOptionPane.showMessageDialog(mainPanel, "登录成功，可以查询成绩和使用其他功能。",
                            "登录成功", JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception error) {
                SwingUtilities.invokeLater(() -> {
                    restartButton.setEnabled(true);
                    showError("会话校验失败：" + error.getMessage());
                });
            }
        }, "session-validator").start();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setForeground(new Color(180, 45, 45));
        mainGUI.info(message);
    }

    private String shorten(String location) {
        return location.length() <= 120 ? location : location.substring(0, 117) + "...";
    }
}
