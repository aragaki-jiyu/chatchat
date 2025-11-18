import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class ChatClient {

    volatile boolean lastIdCheckOk = false;
    volatile String lastCheckedId = null;

    String serverIp;
    int serverPort;

    Scanner in;
    PrintWriter out;

    // 로그인 다이얼로그 레퍼런스 (필요하면)
    JDialog loginDialog = null;

    JFrame frame = new JFrame("ChatChat");
    JTextField textField = new JTextField(50);
    JTextArea messageArea = new JTextArea(16, 50);

    public ChatClient() {

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(textField, BorderLayout.CENTER);
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
            } catch (Exception e) { }
        }

        textField.setEditable(false);
        messageArea.setEditable(false);

        frame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        frame.pack();

        textField.addActionListener(e -> {
            if (out != null) out.println(textField.getText());
            textField.setText("");
        });
    }

    /** ---------------------- 로그인 창 (이제 반환값 없음, 버튼에서 직접 로그인 전송) ---------------------- */
    private void showLoginDialog() {

        // 반드시 EDT에서 호출되어야 함. handleServerMessage 쪽에서 SwingUtilities.invokeLater로 호출함.
        loginDialog = new JDialog(frame, "Login", true);
        JDialog dialog = loginDialog;

        dialog.setSize(400, 220);
        dialog.setLayout(new GridLayout(4, 1));

        JTextField idField = new JTextField();
        JPasswordField pwField = new JPasswordField();
        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register");

        JPanel idPanel = new JPanel(new BorderLayout());
        idPanel.add(new JLabel("ID:"), BorderLayout.WEST);
        idPanel.add(idField, BorderLayout.CENTER);

        JPanel pwPanel = new JPanel(new BorderLayout());
        pwPanel.add(new JLabel("Password:"), BorderLayout.WEST);
        pwPanel.add(pwField, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.add(loginBtn);
        bottom.add(registerBtn);

        dialog.add(idPanel);
        dialog.add(pwPanel);
        dialog.add(new JLabel(" "));
        dialog.add(bottom);

        // 로그인 버튼: EDT에서 직접 서버로 LOGIN 전송
        loginBtn.addActionListener(e -> {
            String id = idField.getText().trim();
            String pw = new String(pwField.getPassword());
            if (id.isEmpty() || pw.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "아이디와 비밀번호를 입력하세요.");
                return;
            }
            if (out != null) {
                out.println("LOGIN " + id + " " + pw);
            }
            dialog.dispose();
        });

        // 레지스터 버튼: 현재 다이얼로그를 숨기고(모달 창 사이클), 회원가입 창으로 이동
        registerBtn.addActionListener(e -> {
            dialog.setVisible(false);
            showRegisterDialog(); // showRegisterDialog도 EDT에서 동작하도록 설계되어 있음
            dialog.setVisible(true);
        });

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    /** ---------------------- 회원가입 창 ---------------------- */
    private void showRegisterDialog() {

        JDialog dialog = new JDialog(frame, "Register", true);
        dialog.setSize(420, 300);
        dialog.setLayout(new GridLayout(6, 2, 4, 4));

        JTextField idField = new JTextField();
        JButton checkBtn = new JButton("Check ID");

        JTextField nameField = new JTextField();
        JTextField emailField = new JTextField();
        JPasswordField pwField = new JPasswordField();

        JLabel checkResult = new JLabel(" ");

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

        checkBtn.addActionListener(e -> {
            String id = idField.getText().trim();
            if (id.isEmpty()) {
                SwingUtilities.invokeLater(() -> checkResult.setText("❌ ID를 입력하세요"));
                return;
            }

            lastCheckedId = id;
            if (out != null) out.println("CHECKID " + id);
        });

        okBtn.addActionListener(e -> {
            String id = idField.getText().trim();

            if (!id.equals(lastCheckedId) || !lastIdCheckOk) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(frame, "ID 중복확인을 먼저 해주세요!")
                );
                return;
            }

            String pw = new String(pwField.getPassword());
            String name = nameField.getText().trim();
            String email = emailField.getText().trim();

            if (out != null) {
                out.println("REGISTER " + id + " " + pw + " " + name + " " + email);
            }

            dialog.dispose();
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    /** ---------------------- 서버 메시지 처리 ---------------------- */
    private void handleServerMessage(String line) {

        if (line.equals("BYE")) {
            SwingUtilities.invokeLater(() -> frame.dispose());
            return;
        }

        if (line.equals("LOGIN")) {
            // UI는 반드시 EDT에서 띄움 — 수신 스레드에서 직접 다이얼로그를 띄우지 않음
            SwingUtilities.invokeLater(this::showLoginDialog);
            return;
        }

        else if (line.equals("LOGINFAIL")) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "❌ 로그인 실패! 다시 시도하세요.")
            );
            // 로그인 실패 시 다시 로그인 창 띄우기 (EDT에서)
            SwingUtilities.invokeLater(this::showLoginDialog);
            return;
        }


        else if (line.equals("NEEDREGISTER")) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "❌ 계정이 존재하지 않습니다.")
            );
            SwingUtilities.invokeLater(this::showRegisterDialog);
            return;
        }

        else if (line.equals("REGISTERSUCCESS")) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "✔ 회원가입 완료! 다시 로그인 해주세요.")
            );
            return;
        }

        else if (line.startsWith("REGFAIL")) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "❌ 회원가입 실패: " + line.substring(7))
            );
            return;
        }

        else if (line.startsWith("NAMEACCEPTED")) {
            final String id = line.substring(13);
            SwingUtilities.invokeLater(() -> {
                frame.setTitle("ChatChat - " + id);
                textField.setEditable(true);
            });
            return;
        }

        else if (line.startsWith("MESSAGE")) {
            final String msg = line.substring(8);
            SwingUtilities.invokeLater(() ->
                    messageArea.append(msg + "\n")
            );
            return;
        }

        else if (line.equals("IDOK")) {
            lastIdCheckOk = true;
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "✔ 사용 가능한 ID입니다!")
            );
            return;
        }

        else if (line.equals("IDUSED")) {
            lastIdCheckOk = false;
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "❌ 이미 사용 중인 ID입니다!")
            );
            return;
        }

        // 기타: 로그용
        System.out.println("Unhandled from server: " + line);
    }

    /** ---------------------- 클라이언트 실행 ---------------------- */
    private void run() throws IOException {

        Socket socket = new Socket(serverIp, serverPort);
        in = new Scanner(socket.getInputStream());
        out = new PrintWriter(socket.getOutputStream(), true);

        // 핵심: 수신 스레드 분리 (이 스레드는 UI와 분리되어 동작)
        new Thread(() -> {
            try {
                while (in.hasNextLine()) {
                    String line = in.nextLine();
                    handleServerMessage(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void main(String[] args) throws Exception {
        ChatClient client = new ChatClient();
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.frame.setVisible(true);
        client.run();
    }
}


}



