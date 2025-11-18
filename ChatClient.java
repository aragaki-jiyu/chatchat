import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class ChatClient {

    // 최근 ID 체크 결과 저장 (중복검사 후 회원가입 버튼 활성화에 사용)
    volatile boolean lastIdCheckOk = false;
    volatile String lastCheckedId = null;

    // 서버 접속 정보
    String serverIp;
    int serverPort;

    // 서버와의 입출력 스트림
    Scanner in;
    PrintWriter out;

    // 로그인 창을 다시 띄울 때 사용
    JDialog loginDialog = null;

    // 메인 채팅 UI
    JFrame frame = new JFrame("ChatChat");
    JTextField textField = new JTextField(50);
    JTextArea messageArea = new JTextArea(16, 50);

    public ChatClient() {

        // ▼ 채팅 입력창 panel 구성
        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(textField, BorderLayout.CENTER);
        frame.getContentPane().add(southPanel, BorderLayout.SOUTH);

        // 기본 서버 주소
        serverIp = "localhost";
        serverPort = 59001;

        // ▼ server_info.dat 에서 서버 정보 로드 (있을 경우)
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

        // 초기에는 메시지 입력 비활성화 (로그인 성공해야 활성화됨)
        textField.setEditable(false);
        messageArea.setEditable(false);

        frame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        frame.pack();

        // 엔터로 메시지 전송
        textField.addActionListener(e -> {
            if (out != null) out.println(textField.getText());
            textField.setText("");
        });
    }

    // ===================================================================================
    //                               로그인 창 표시
    // ===================================================================================
    private void showLoginDialog() {

        // UI는 반드시 EDT에서 실행
        loginDialog = new JDialog(frame, "Login", true);
        JDialog dialog = loginDialog;

        dialog.setSize(400, 220);
        dialog.setLayout(new GridLayout(4, 1));

        JTextField idField = new JTextField();
        JPasswordField pwField = new JPasswordField();
        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register");

        // ▼ ID 입력 필드
        JPanel idPanel = new JPanel(new BorderLayout());
        idPanel.add(new JLabel("ID:"), BorderLayout.WEST);
        idPanel.add(idField, BorderLayout.CENTER);

        // ▼ 비밀번호 입력 필드
        JPanel pwPanel = new JPanel(new BorderLayout());
        pwPanel.add(new JLabel("Password:"), BorderLayout.WEST);
        pwPanel.add(pwField, BorderLayout.CENTER);

        // ▼ 버튼 영역
        JPanel bottom = new JPanel();
        bottom.add(loginBtn);
        bottom.add(registerBtn);

        // ▼ 다이얼로그에 요소 배치
        dialog.add(idPanel);
        dialog.add(pwPanel);
        dialog.add(new JLabel(" ")); // 여백
        dialog.add(bottom);

        // ------------------ 로그인 버튼 동작 ------------------
        loginBtn.addActionListener(e -> {
            String id = idField.getText().trim();
            String pw = new String(pwField.getPassword());

            if (id.isEmpty() || pw.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "아이디와 비밀번호를 입력하세요.");
                return;
            }

            // 서버로 LOGIN 전송
            if (out != null) {
                out.println("LOGIN " + id + " " + pw);
            }

            dialog.dispose();
        });

        // ------------------ 회원가입 버튼 ------------------
        registerBtn.addActionListener(e -> {
            dialog.setVisible(false);  // 로그인 창 잠시 숨기기
            showRegisterDialog();      // 회원가입 창 띄우기
            dialog.setVisible(true);   // 회원가입 후 다시 로그인 창 복귀
        });

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    // ===================================================================================
    //                                 회원가입 창
    // ===================================================================================
    private void showRegisterDialog() {

        JDialog dialog = new JDialog(frame, "Register", true);
        dialog.setSize(420, 300);
        dialog.setLayout(new GridLayout(6, 2, 4, 4));

        JTextField idField = new JTextField();
        JButton checkBtn = new JButton("Check ID"); // 아이디 중복확인 버튼

        JTextField nameField = new JTextField();
        JTextField emailField = new JTextField();
        JPasswordField pwField = new JPasswordField();

        JLabel checkResult = new JLabel(" "); // 중복검사 결과 표시

        // ------- UI 구성 -------
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

        // ---------------------- ID 중복확인 버튼 ----------------------
        checkBtn.addActionListener(e -> {
            String id = idField.getText().trim();
            if (id.isEmpty()) {
                SwingUtilities.invokeLater(() -> checkResult.setText("❌ ID를 입력하세요"));
                return;
            }

            lastCheckedId = id;
            if (out != null) out.println("CHECKID " + id); // 서버에게 중복확인 요청
        });

        // ---------------------- 회원가입 버튼 ----------------------
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

        // ---------------------- 취소 버튼 ----------------------
        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }


    // ===================================================================================
    //                           서버에서 오는 메시지 처리
    // ===================================================================================
    private void handleServerMessage(String line) {

        // --------------- 서버가 BYE를 보낸 경우 (종료) ---------------
        if (line.equals("BYE")) {
            SwingUtilities.invokeLater(() -> frame.dispose());
            return;
        }

        // --------------- 서버가 로그인 요청 시 로그인 창 띄우기 ---------------
        if (line.equals("LOGIN")) {
            SwingUtilities.invokeLater(this::showLoginDialog);
            return;
        }

        // 로그인 실패
        else if (line.equals("LOGINFAIL")) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "❌ 로그인 실패! 다시 시도하세요.")
            );
            SwingUtilities.invokeLater(this::showLoginDialog);
            return;
        }

        // 계정 없음 → 회원가입 안내
        else if (line.equals("NEEDREGISTER")) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "❌ 계정이 존재하지 않습니다.")
            );
            SwingUtilities.invokeLater(this::showRegisterDialog);
            return;
        }

        // 회원가입 성공
        else if (line.equals("REGISTERSUCCESS")) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "✔ 회원가입 완료! 다시 로그인 해주세요.")
            );
            return;
        }

        // 회원가입 실패
        else if (line.startsWith("REGFAIL")) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "❌ 회원가입 실패: " + line.substring(7))
            );
            return;
        }

        // 로그인 성공
        else if (line.startsWith("NAMEACCEPTED")) {
            final String id = line.substring(13);
            SwingUtilities.invokeLater(() -> {
                frame.setTitle("ChatChat - " + id);
                textField.setEditable(true); // 메시지 입력 가능
            });
            return;
        }

        // 일반 메시지
        else if (line.startsWith("MESSAGE")) {
            final String msg = line.substring(8);
            SwingUtilities.invokeLater(() ->
                    messageArea.append(msg + "\n")
            );
            return;
        }

        // ID 사용 가능
        else if (line.equals("IDOK")) {
            lastIdCheckOk = true;
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "✔ 사용 가능한 ID입니다!")
            );
            return;
        }

        // ID 중복
        else if (line.equals("IDUSED")) {
            lastIdCheckOk = false;
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, "❌ 이미 사용 중인 ID입니다!")
            );
            return;
        }

        // 기타 메시지 로그
        System.out.println("Unhandled from server: " + line);
    }


    // ===================================================================================
    //                           클라이언트 실행 (서버 연결)
    // ===================================================================================
    private void run() throws IOException {

        // 서버 연결
        Socket socket = new Socket(serverIp, serverPort);
        in = new Scanner(socket.getInputStream());
        out = new PrintWriter(socket.getOutputStream(), true);

        // 서버에서 오는 메시지 처리 스레드
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

    // ===================================================================================
    //                                        MAIN
    // ===================================================================================
    public static void main(String[] args) throws Exception {
        ChatClient client = new ChatClient();
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.frame.setVisible(true);
        client.run();
    }
}




