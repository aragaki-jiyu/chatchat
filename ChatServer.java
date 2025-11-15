import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {

    private static Set<String> names = new HashSet<>();
    private static Set<PrintWriter> writers = new HashSet<>();

    // 계정 파일
    private static File accountFile = new File("accounts.dat");
    private static Map<String, String> accounts = new HashMap<>();

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

    /** accounts.dat 로드 */
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

    /** accounts.dat 저장 */
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

        public Handler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new Scanner(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);

                // 로그인 절차 시작
                while (true) {
                    out.println("LOGIN");
                    String line = in.nextLine();

                    if (line.startsWith("LOGIN")) {
                        // LOGIN id pw
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
                        break; // 로그인 성공
                    }

                    else if (line.startsWith("REGISTER")) {
                        // REGISTER id pw
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

                out.println("NAMEACCEPTED " + name);
                writers.add(out);

                for (PrintWriter writer : writers)
                    writer.println("MESSAGE " + name + " has joined");

                while (true) {
                    String msg = in.nextLine();
                    if (msg.toLowerCase().startsWith("/quit")) break;

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
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
}
