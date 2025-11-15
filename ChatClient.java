import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.util.*;

import java.awt.BorderLayout;
import javax.swing.*;

public class ChatClient {

    String serverIp;
    int serverPort;

    Scanner in;
    PrintWriter out;

    JFrame frame = new JFrame("ChatChat");
    JTextField textField = new JTextField(50);
    JTextArea messageArea = new JTextArea(16, 50);

    public ChatClient() {

        serverIp = "localhost";
        serverPort = 59001;

        File configFile = new File("server_info.dat");

        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line = reader.readLine();
                if (line != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length == 2) {
                        serverIp = parts[0];
                        serverPort = Integer.parseInt(parts[1]);
                    }
                }
            } catch (Exception e) {
                System.out.println("설정 파일 읽는 중 오류");
            }
        }

        textField.setEditable(false);
        messageArea.setEditable(false);

        frame.getContentPane().add(textField, BorderLayout.SOUTH);
        frame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        frame.pack();

        textField.addActionListener(e -> {
            out.println(textField.getText());
            textField.setText("");
        });
    }

    private String[] showLoginDialog() {
        JTextField idField = new JTextField();
        JPasswordField pwField = new JPasswordField();
        Object[] fields = {
                "ID:", idField,
                "Password:", pwField
        };

        int option = JOptionPane.showConfirmDialog(frame, fields, "Login", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            return new String[]{idField.getText(), new String(pwField.getPassword())};
        }
        return null;
    }

    private String[] showRegisterDialog() {
        JTextField idField = new JTextField();
        JPasswordField pwField = new JPasswordField();
        Object[] fields = {
                "New ID:", idField,
                "New Password:", pwField
        };

        int option = JOptionPane.showConfirmDialog(frame, fields, "Register", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            return new String[]{idField.getText(), new String(pwField.getPassword())};
        }
        return null;
    }

    private void run() throws IOException {
        try {
            Socket socket = new Socket(serverIp, serverPort);
            in = new Scanner(socket.getInputStream());
            out = new PrintWriter(socket.getOutputStream(), true);

            while (in.hasNextLine()) {
                String line = in.nextLine();

                if (line.equals("LOGIN")) {
                    String[] user = showLoginDialog();
                    if (user != null)
                        out.println("LOGIN " + user[0] + " " + user[1]);
                }

                else if (line.equals("LOGINFAIL")) {
                    JOptionPane.showMessageDialog(frame, "로그인 실패! 다시 시도하세요.");
                }

                else if (line.equals("NEEDREGISTER")) {
                    JOptionPane.showMessageDialog(frame, "계정 없음! 새로 만들기.");
                    String[] reg = showRegisterDialog();
                    if (reg != null)
                        out.println("REGISTER " + reg[0] + " " + reg[1]);
                }

                else if (line.startsWith("REGFAIL")) {
                    JOptionPane.showMessageDialog(frame, "회원가입 실패: ID 중복");
                }

                else if (line.equals("REGISTERSUCCESS")) {
                    JOptionPane.showMessageDialog(frame, "회원가입 완료! 로그인 해주세요.");
                }

                else if (line.startsWith("NAMEACCEPTED")) {
                    frame.setTitle("ChatChat - " + line.substring(13));
                    textField.setEditable(true);
                }

                else if (line.startsWith("MESSAGE")) {
                    messageArea.append(line.substring(8) + "\n");
                }
            }

        } finally {
            frame.setVisible(false);
            frame.dispose();
        }
    }

    public static void main(String[] args) throws Exception {
        ChatClient client = new ChatClient();
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.frame.setVisible(true);
        client.run();
    }
}
