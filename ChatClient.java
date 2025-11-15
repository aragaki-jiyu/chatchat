import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import java.util.*;

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class ChatClient {

    String serverIp;
    int serverPort;

    Scanner in;
    PrintWriter out;

    JFrame frame = new JFrame("ChatChat");
    JTextField textField = new JTextField(50);
    JTextArea messageArea = new JTextArea(16, 50);

    /**
     * 서버 정보 읽기 및 GUI 초기화
     */
    public ChatClient() {

        // 기본값
        serverIp = "localhost";
        serverPort = 59001;

        // 설정 파일
        File configFile = new File("server_info.dat");

        // 파일이 있으면 읽기
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line = reader.readLine();
                if (line != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length == 2) {
                        serverIp = parts[0];
                        try {
                            serverPort = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException e) {
                            System.out.println("포트 번호가 잘못됨 → 기본값 9999 사용.");
                            serverPort = 9999;
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("설정 파일 읽기 오류 → 기본값 사용.");
            }
        }
        // 없으면 생성
        else {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                writer.write(serverIp + " " + serverPort);
                System.out.println("server_info.dat 생성됨(기본값).");
            } catch (IOException e) {
                System.out.println("설정 파일 생성 실패.");
            }
        }

        // GUI 설정
        textField.setEditable(false);
        messageArea.setEditable(false);

        frame.getContentPane().add(textField, BorderLayout.SOUTH);
        frame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        frame.pack();

        textField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                out.println(textField.getText());
                textField.setText("");
            }
        });
    }

    private String getName() {
        return JOptionPane.showInputDialog(
                frame,
                "Choose a screen name:",
                "Screen name selection",
                JOptionPane.PLAIN_MESSAGE
        );
    }

    /**
     * 서버 접속 + 메시지 처리
     */
    private void run() throws IOException {
        try {
            System.out.println("🔌 Connecting to " + serverIp + ":" + serverPort);
            Socket socket = new Socket(serverIp, serverPort);

            in = new Scanner(socket.getInputStream());
            out = new PrintWriter(socket.getOutputStream(), true);

            while (in.hasNextLine()) {
                String line = in.nextLine();
                if (line.startsWith("SUBMITNAME")) {
                    out.println(getName());
                } else if (line.startsWith("NAMEACCEPTED")) {
                    this.frame.setTitle("Chatter - " + line.substring(13));
                    textField.setEditable(true);
                } else if (line.startsWith("MESSAGE")) {
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
