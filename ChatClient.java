import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class ChatClient {
    volatile boolean lastIdCheckOk = false;
    volatile String lastCheckedId = null;

    String[] result[];
    String serverIp;
    int serverPort;

    Scanner in;
    PrintWriter out;

    JFrame frame = new JFrame("ChatChat");
    JTextField textField = new JTextField(50);
    JTextArea messageArea = new JTextArea(16, 50);

    public ChatClient() {
        JButton logoutBtn = new JButton("Logout");

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(textField, BorderLayout.CENTER);
        southPanel.add(logoutBtn, BorderLayout.EAST);

        frame.getContentPane().add(southPanel, BorderLayout.SOUTH);

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


        logoutBtn.addActionListener(e -> {
            try {
                out.println("LOGOUT");
                textField.setEditable(false);

                // 소켓 종료
                in.close();
                out.close();

                // UI 초기화
                messageArea.setText("");
                frame.setTitle("ChatChat");

                // 다시 로그인 요청
                new Thread(() -> {
                    try {
                        run(); // 재접속
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
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
            dialog.setVisible(false);  // ❗ 로그인창 닫지 말고 숨기기만!

            String[] reg = showRegisterDialog();

            dialog.setVisible(true);   // ❗ register 창 닫히면 로그인창 다시 보이게!

            if (reg != null) {
                out.println("REGISTER " + reg[0] + " " + reg[1] + " " + reg[2] + " " + reg[3]);
            }
        });


        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);

        return result[0];
    }


    /** 회원가입 창 */
    private String[] showRegisterDialog() {

        JDialog dialog = new JDialog(frame, "Register", true);
        dialog.setSize(400, 300);
        dialog.setLayout(new GridLayout(6, 2));

        JTextField idField = new JTextField();
        JButton checkBtn = new JButton("Check ID");

        JTextField nameField = new JTextField();
        JTextField emailField = new JTextField();
        JPasswordField pwField = new JPasswordField();

        JLabel checkResult = new JLabel(" ");

        final String[][] result = new String[1][];  // ★ 반드시 있어야 함

        dialog.add(new JLabel("ID:"));
        dialog.add(idField);

        dialog.add(checkBtn);
        dialog.add(checkResult);

        dialog.add(new JLabel("Name:"));
        dialog.add(nameField);

        dialog.add(new JLabel("Email:"));
        dialog.add(emailField);

        dialog.add(new JLabel("Password:"));
        dialog.add(pwField);

        JButton okBtn = new JButton("Register");
        JButton cancelBtn = new JButton("Cancel");
        dialog.add(okBtn);
        dialog.add(cancelBtn);

        // ID 중복 체크
        checkBtn.addActionListener(e -> {
            String id = idField.getText().trim();
            if (id.isEmpty()) {
                checkResult.setText("❌ ID를 입력하세요");
                return;
            }

            lastCheckedId = id;
            out.println("CHECKID " + id);
        });

        // 회원가입 버튼
        okBtn.addActionListener(e -> {
            String id = idField.getText().trim();

            if (!id.equals(lastCheckedId) || !lastIdCheckOk) {
                JOptionPane.showMessageDialog(frame, "ID 중복확인을 먼저 해주세요!");
                return;
            }

            result[0] = new String[]{
                    id,
                    new String(pwField.getPassword()),
                    nameField.getText().trim(),
                    emailField.getText().trim()
            };

            dialog.dispose();
        });

        // ❗❗ Cancel 누르면 그냥 null 반환하고 종료
        cancelBtn.addActionListener(e -> {
            result[0] = null;   // ★ 명확히 null 반환
            dialog.dispose();
        });

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);

        return result[0];        // ★ Cancel이면 null이 반환됨
    }



    private void run() throws IOException {
        try {
            Socket socket = new Socket(serverIp, serverPort);
            in = new Scanner(socket.getInputStream());
            out = new PrintWriter(socket.getOutputStream(), true);

            while (in.hasNextLine()) {
                String line = in.nextLine();

                if (line.equals("BYE")) {
                    break; // 서버가 LOGOUT 처리 후 보내는 메시지
                }

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
                    JOptionPane.showMessageDialog(frame, "❌ 계정이 존재하지 않습니다.");

                    String[] reg = showRegisterDialog();

                    if (reg == null) {
                        // 🔥 Cancel 시 다시 로그인 요청하도록 서버에 알림
                        out.println("CANCELREGISTER");
                        continue;  // 로그인 화면으로 돌아감
                    }

                    out.println("REGISTER " + reg[0] + " " + reg[1] + " " + reg[2] + " " + reg[3]);
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

                else if (line.equals("IDOK")) {
                    lastIdCheckOk = true;
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(frame, "✔ 사용 가능한 ID입니다!")
                    );
                }

                else if (line.equals("IDUSED")) {
                    lastIdCheckOk = false;
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(frame, "❌ 이미 사용 중인 ID입니다!")
                    );
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

}



