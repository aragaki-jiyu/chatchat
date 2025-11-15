import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {

    private static Set<String> names = new HashSet<>();
    private static Set<PrintWriter> writers = new HashSet<>();

    private static File accountFile = new File("accounts.dat");
    private static Map<String, String> accounts = new HashMap<>();

    // ★ 추가: 유저별 writer 저장
    private static Map<String, PrintWriter> userWriters = new HashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running...");

        loadAccounts();

        ExecutorService pool = Executors.newFixedThreadPool(500);
        try (ServerSocket listener = new ServerSocket(59001)) {
            while (true) {
                pool.execute(new Handler(listener.accept()));
            }
        }
    }

    private static void loadAccounts() {
        if (!accountFile.exists()) return;

        try (Scanner scan = new Scanner(accountFile)) {
            while (scan.hasNext()) {
                String id = scan.next();
                String pw = scan.next();
                accounts.put(id, pw);
            }
        } catch (Exception e) {
            System.out.println("계정 파일 로드 실패: " + e);
        }
    }

    private static synchronized void saveAccount(String id, String pw) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(accountFile, true))) {
            writer.println(id + " " + pw);
        } catch (IOException e) {
            System.out.println("계정 저장 실패");
        }
    }

    private static class Handler implements Runnable {
        private String name;
        private Socket socket;
        private Scanner in;
        private PrintWriter out;

        public Handler(Socket socket) { this.socket = socket; }

        public void run() {
            try {
                in = new Scanner(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);

                // 로그인 절차
                while (true) {
                    out.println("LOGIN");
                    String line = in.nextLine();

                    if (line.startsWith("LOGIN")) {
                        String[] parts = line.split(" ");
                        if (parts.length != 3) continue;

                        String id = parts[1];
                        String pw = parts[2];

                        if (!accounts.containsKey(id)) {
                            out.println("NEEDREGISTER");
                            continue;
                        }

                        if (!accounts.get(id).equals(pw)) {
                            out.println("LOGINFAIL");
                            continue;
                        }

                        name = id;
                        break;
                    }

                    else if (line.startsWith("REGISTER")) {
                        String[] parts = line.split(" ");
                        if (parts.length != 3) continue;

                        String id = parts[1];
                        String pw = parts[2];

                        if (accounts.containsKey(id)) {
                            out.println("REGFAIL DuplicateID");
                        } else {
                            accounts.put(id, pw);
                            saveAccount(id, pw);
                            out.println("REGISTERSUCCESS");
                        }
                    }
                }

                synchronized (names) {
                    names.add(name);
                }

                // ★ userWriters에 개별 Writer 저장
                userWriters.put(name, out);

                out.println("NAMEACCEPTED " + name);
                writers.add(out);

                for (PrintWriter writer : writers)
                    writer.println("MESSAGE " + name + " has joined");

                // 메시지 처리 루프
                while (true) {

                    String msg = in.nextLine();
                    if (msg.toLowerCase().startsWith("/quit")) break;

                    // ★ Whisper 처리: /w 대상 메시지...
                    if (msg.startsWith("/w ")) {
                        handleWhisper(msg);
                        continue;
                    }

                    // 일반 메시지 전체 전송
                    for (PrintWriter writer : writers)
                        writer.println("MESSAGE " + name + ": " + msg);
                }

            } catch (Exception e) {
                System.out.println(e);
            } finally {
                if (name != null) {
                    System.out.println(name + " disconnected");
                    names.remove(name);
                }
                writers.remove(out);
                userWriters.remove(name);
                try { socket.close(); } catch (IOException e) {}
            }
        }

        /** ★ Whisper 처리 함수 추가 */
        private void handleWhisper(String msg) {
            // 형식: /w 대상유저 내용...
            String[] parts = msg.split(" ", 3);

            if (parts.length < 3) {
                out.println("MESSAGE Whisper 사용법: /w [유저명] [메시지]");
                return;
            }

            String targetUser = parts[1];
            String whisperMsg = parts[2];

            PrintWriter targetWriter = userWriters.get(targetUser);

            if (targetWriter == null) {
                out.println("MESSAGE ⚠ 상대방 " + targetUser + " 님이 존재하지 않습니다.");
                return;
            }

            // 발신자에게 표시
            out.println("MESSAGE (귓속말→" + targetUser + ") " + whisperMsg);

            // 상대에게만 표시
            targetWriter.println("MESSAGE (귓속말←" + name + ") " + whisperMsg);
        }
    }
}
