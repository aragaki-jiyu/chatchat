import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
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
                    String[] parts = line.split("\\s+");
                    serverIp = parts[0];
                    serverPort = Integer.parseInt(parts[1]);
                }
            } catch (Exception e) {}
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

    /** 로그인 창 (회원가입 버튼 포함) */
    private String[] showLoginDialog() {

        JDialog dialog = new JDialog(frame, "Login", true);
        dialog.setSize(400, 200);
        dialog.setLayout(new GridLayout(4, 1));

        JTextField idField = new JTextField();
        JPasswordField pwField = new JPasswordField();
        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register");

        dialog.add(new JLabel("ID:"));
        dialog.add(idField);
        dialog.add(new JLabel("Password:"));
        dialog.add(pwField);

        JPanel bottom = new JPanel();
        bottom.add(loginBtn);
        bottom.add(registerBtn);
        dialog.add(bottom);

        final String[][] result = new String[1][];

        // 로그인 버튼
        loginBtn.addActionListener(e -> {
            result[0] = new String[]{idField.getText(), new String(pwField.getPassword())};
            dialog.dispose();
        });

        // 회원가입 버튼 → 별도 회원가입 창
        registerBtn.addActionListener(e -> {
            dialog.dispose();
            String[] reg = showRegisterDialog();
            if (reg != null) {
                out.println("REGISTER " + reg[0] + " " + reg[1]);
            }
        });

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);

        return result[0];
    }

    /** 회원가입 창 */
    private String[] showRegisterDialog() {
        JTextField idField = new JTextField();
        JPasswordField pwField = new JPasswordField();

        Object[] fields = {
                "New ID:", idField,
                "New Password:", pwField
        };

        int option = JOptionPane.showConfirmDialog(
                frame, fields, "Register", JOptionPane.OK_CANCEL_OPTION
        );

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
                    String[] login = showLoginDialog();
                    if (login != null) {
                        out.println("LOGIN " + login[0] + " " + login[1]);
                    }
                }

                else if (line.equals("LOGINFAIL")) {
                    JOptionPane.showMessageDialog(frame, "❌ 로그인 실패! 다시 시도하세요.");
                }

                else if (line.equals("NEEDREGISTER")) {
                    JOptionPane.showMessageDialog(frame, "❌ 계정이 존재하지 않습니다. 회원가입을 해주세요.");
                    String[] reg = showRegisterDialog();
                    if (reg != null)
                        out.println("REGISTER " + reg[0] + " " + reg[1]);
                }

                else if (line.startsWith("REGFAIL")) {
                    JOptionPane.showMessageDialog(frame, "❌ 회원가입 실패: ID 중복");
                }

                else if (line.equals("REGISTERSUCCESS")) {
                    JOptionPane.showMessageDialog(frame, "✔ 회원가입 완료! 로그인하세요.");
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
