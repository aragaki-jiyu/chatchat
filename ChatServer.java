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

	// id → [pw, name, email]
	private static Map<String, String[]> accounts = new HashMap<>();

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
		accounts.clear();
		if (!accountFile.exists()) {
			System.out.println("accounts.dat 없음 — 새로 시작");
			return;
		}

		try (BufferedReader br = new BufferedReader(new FileReader(accountFile))) {
			String line;
			int lineNo = 0;
			while ((line = br.readLine()) != null) {
				lineNo++;
				line = line.trim();
				if (line.isEmpty()) continue;
				// 공백 기준으로 토큰 분리 (연속 공백 무시)
				String[] tok = line.split("\\s+");
				if (tok.length == 2) {
					// 기존 형식: id pw
					String id = tok[0];
					String pw = tok[1];
					accounts.put(id, new String[]{pw, "", ""});
				} else if (tok.length >= 4) {
					// 새 형식: id pw name email (name/email에 공백이 없다고 가정)
					String id = tok[0];
					String pw = tok[1];
					String name = tok[2];
					String email = tok[3];
					accounts.put(id, new String[]{pw, name, email});
				} else {
					System.out.println("accounts.dat: 파싱 불가한 라인 " + lineNo + ": " + line);
				}
			}
			System.out.println("accounts loaded: " + accounts.size());
		} catch (Exception e) {
			System.out.println("계정 파일 로드 실패: " + e);
		}
	}

	private static synchronized void saveAccount(String id, String pw, String name, String email) {
		try (PrintWriter writer = new PrintWriter(new FileWriter(accountFile, true))) {
			// 공백 문제를 피하려면 name/email에 공백이 있을 경우 처리 필요 — 지금은 간단히 띄우기
			writer.println(id + " " + pw + " " + name + " " + email);
			writer.flush();
		} catch (IOException e) {
			System.out.println("계정 저장 실패: " + e);
		}
	}


	private static class Handler implements Runnable {
		private String id;
		private Socket socket;
		private Scanner in;
		private PrintWriter out;

		public Handler(Socket socket) { this.socket = socket; }


		public void run() {
			try {
				in = new Scanner(socket.getInputStream());
				out = new PrintWriter(socket.getOutputStream(), true);

				// --- 처음에만 LOGIN 요청을 보냄 (한 번만) ---
				out.println("LOGIN");

				// 이제 클라이언트에서 보내는 명령들을 계속 수신하며 처리
				while (true) {
					if (!in.hasNextLine()) {
						// 클라이언트가 연결을 끊었으면 루프 탈출
						return;
					}
					String line = in.nextLine().trim();
					if (line.isEmpty()) continue;

					// ------------------ CHECKID 처리 ------------------
					if (line.startsWith("CHECKID ")) {
						String[] p = line.split(" ", 2);
						if (p.length == 2) {
							String checkId = p[1].trim();
							boolean used;
							synchronized (accounts) {
								used = accounts.containsKey(checkId);
							}
							if (used) out.println("IDUSED");
							else out.println("IDOK");
						}
						continue;
					}


					// ------------------ REGISTER 처리 ------------------
					if (line.startsWith("REGISTER ")) {
						// 기대 형식: REGISTER id pw name email
						String[] p = line.split(" ");
						if (p.length >= 5) { // name/email에 공백이 없다 가정
							String newId = p[1];
							String newPw = p[2];
							String newName = p[3];
							String newEmail = p[4];
							synchronized (accounts) {
								if (accounts.containsKey(newId)) {
									out.println("REGFAIL DuplicateID");
								} else {
									accounts.put(newId, new String[]{newPw, newName, newEmail});
									saveAccount(newId, newPw, newName, newEmail);
									out.println("REGISTERSUCCESS");
								}
							}
						} else {
							out.println("REGFAIL BadFormat");
						}
						continue;
					}

					// ------------------ LOGIN 처리 ------------------
					if (line.startsWith("LOGIN ")) {
						String[] parts = line.split(" ");
						if (parts.length == 3) {
							String loginId = parts[1];
							String loginPw = parts[2];

							if (!accounts.containsKey(loginId)) {
								out.println("NEEDREGISTER");
								// DON'T send LOGIN again here
								continue;
							}

							String[] stored = accounts.get(loginId);
							if (!stored[0].equals(loginPw)) {
								out.println("LOGINFAIL");
								continue;
							}

							synchronized (names) {
								if (names.contains(loginId)) {
									out.println("ALREADYLOGGEDIN");
									continue;
								}
							}

							// 로그인 성공하면 밖으로 나와 채팅 상태로 이동
							this.id = loginId; // 또는 this.name = loginId (기존 사용명과 맞출 것)
							break; // 로그인 성공 -> 채팅 루프 진입
						} else {
							out.println("LOGINFAIL");
							continue;
						}
					}

					// 다른 알려지지 않은 명령은 무시하거나 안내 메시지 전송
					// out.println("MESSAGE Unknown command");


					//---
					if (line.equals("CANCELREGISTER")) {
						out.println("LOGIN");
						continue;
					}

				}

				// 여기까지 도달하면 로그인 성공 (id 변수에 사용자 아이디 저장)
				synchronized (names) {
					names.add(id);
				}

				userWriters.put(id, out);
				writers.add(out);

				out.println("NAMEACCEPTED " + id);

				for (PrintWriter writer : writers)
					writer.println("MESSAGE " + id + " has joined");

				// ------------------ 채팅 루프 ------------------
				while (true) {
					if (!in.hasNextLine()) break;
					String msg = in.nextLine();
					if (msg == null) break;
					msg = msg.trim();
					if (msg.equalsIgnoreCase("/quit")) break;

					if (msg.startsWith("/w ")) {
						handleWhisper(msg);
						continue;
					}

					for (PrintWriter writer : writers)
						writer.println("MESSAGE " + id + ": " + msg);
				}

			} catch (Exception e) {
				System.out.println(e);
			} finally {
				// 접속 종료 처리
				if (id != null) {
					System.out.println(id + " disconnected");
					names.remove(id);
					userWriters.remove(id);
					writers.remove(out);
					for (PrintWriter writer : writers)
						writer.println("MESSAGE " + id + " has left");
				} else {
					// 로그인 전에 연결이 끊어졌을 경우 (id==null)
					writers.remove(out);
				}
				try { socket.close(); } catch (IOException e) {}
			}
		}


		private void handleWhisper(String msg) {
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

			out.println("MESSAGE (귓속말→" + targetUser + ") " + whisperMsg);
			targetWriter.println("MESSAGE (귓속말←" + id + ") " + whisperMsg);
		}
	}
}

