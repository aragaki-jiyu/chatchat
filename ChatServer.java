import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {

	private static Set<String> names = new HashSet<>();
	private static Set<PrintWriter> writers = new HashSet<>();

	private static File accountFile = new File("accounts.dat");

	// id → [salt, hash, name, email]
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


	// ============================
	//  PASSWORD HASH FUNCTIONS
	// ============================

	private static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) sb.append(String.format("%02x", b));
		return sb.toString();
	}

	private static String generateSalt() {
		byte[] salt = new byte[16];
		new SecureRandom().nextBytes(salt);
		return bytesToHex(salt);
	}

	private static String hashPassword(String password, String salt) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(salt.getBytes("UTF-8"));
		byte[] hashed = md.digest(password.getBytes("UTF-8"));
		return bytesToHex(hashed);
	}


	// ============================
	//    LOAD ACCOUNTS
	// ============================

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

				// id salt:hash name email
				String[] tok = line.split("\\s+");
				if (tok.length >= 4 && tok[1].contains(":")) {
					String id = tok[0];
					String[] sh = tok[1].split(":");
					String salt = sh[0];
					String hash = sh[1];
					String name = tok[2];
					String email = tok[3];
					accounts.put(id, new String[]{salt, hash, name, email});
				} else {
					System.out.println("⚠ 구버전 비밀번호 포맷 발견 — 무시됨: " + line);
				}
			}
			System.out.println("accounts loaded: " + accounts.size());
		} catch (Exception e) {
			System.out.println("계정 파일 로드 실패: " + e);
		}
	}


	// ============================
	//  SAVE ACCOUNT (HASHED)
	// ============================

	private static synchronized void saveAccount(String id, String salt, String hash, String name, String email) {
		try (PrintWriter writer = new PrintWriter(new FileWriter(accountFile, true))) {
			writer.println(id + " " + salt + ":" + hash + " " + name + " " + email);
		} catch (IOException e) {
			System.out.println("계정 저장 실패: " + e);
		}
	}


	// ============================
	//         HANDLER
	// ============================

	private static class Handler implements Runnable {

		private String id;
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

				out.println("LOGIN");

				while (true) {
					if (!in.hasNextLine()) return;
					String line = in.nextLine().trim();
					if (line.isEmpty()) continue;

					// ---------------- CHECKID ----------------
					if (line.startsWith("CHECKID ")) {
						String checkId = line.substring(8).trim();
						boolean used = accounts.containsKey(checkId);
						out.println(used ? "IDUSED" : "IDOK");
						continue;
					}


					// ---------------- REGISTER ----------------
					if (line.startsWith("REGISTER ")) {
						String[] p = line.split(" ");
						if (p.length >= 5) {

							String newId = p[1];
							String pw = p[2];
							String name = p[3];
							String email = p[4];

							synchronized (accounts) {
								if (accounts.containsKey(newId)) {
									out.println("REGFAIL DuplicateID");
								} else {
									String salt = generateSalt();
									String hash = hashPassword(pw, salt);

									accounts.put(newId, new String[]{salt, hash, name, email});
									saveAccount(newId, salt, hash, name, email);

									out.println("REGISTERSUCCESS");
								}
							}
						} else {
							out.println("REGFAIL BadFormat");
						}
						continue;
					}


					// ---------------- LOGIN ----------------
					if (line.startsWith("LOGIN ")) {
						String[] parts = line.split(" ");
						if (parts.length == 3) {
							String loginId = parts[1];
							String loginPw = parts[2];

							if (!accounts.containsKey(loginId)) {
								out.println("NEEDREGISTER");
								continue;
							}

							String[] acc = accounts.get(loginId);
							String salt = acc[0];
							String storedHash = acc[1];

							String inputHash = hashPassword(loginPw, salt);

							if (!storedHash.equals(inputHash)) {
								out.println("LOGINFAIL");
								continue;
							}

							synchronized (names) {
								if (names.contains(loginId)) {
									out.println("ALREADYLOGGEDIN");
									continue;
								}
							}

							this.id = loginId;
							break;
						}
						continue;
					}

					if (line.equals("CANCELREGISTER")) {
						out.println("LOGIN");
						continue;
					}
				}

				// 로그인 완료
				synchronized (names) {
					names.add(id);
				}

				userWriters.put(id, out);
				writers.add(out);

				out.println("NAMEACCEPTED " + id);
				for (PrintWriter w : writers)
					w.println("MESSAGE " + id + " has joined");

				// 채팅 루프
				while (true) {
					if (!in.hasNextLine()) break;
					String msg = in.nextLine().trim();
					if (msg.equals("LOGOUT")) {
						out.println("BYE");
						break;
					}
					if (msg.startsWith("/w ")) {
						handleWhisper(msg);
						continue;
					}

					for (PrintWriter w : writers)
						w.println("MESSAGE " + id + ": " + msg);
				}

			} catch (Exception e) {
				System.out.println(e);
			} finally {
				if (id != null) {
					names.remove(id);
					writers.remove(out);
					userWriters.remove(id);

					for (PrintWriter w : writers)
						w.println("MESSAGE " + id + " has left");
				}

				try { socket.close(); } catch (IOException e) {}
			}
		}

		private void handleWhisper(String msg) {
			String[] p = msg.split(" ", 3);
			if (p.length < 3) {
				out.println("MESSAGE Whisper 사용법: /w [유저명] [메시지]");
				return;
			}

			PrintWriter target = userWriters.get(p[1]);
			if (target == null) {
				out.println("MESSAGE ⚠ 상대방 없음");
				return;
			}

			out.println("MESSAGE (귓→" + p[1] + ") " + p[2]);
			target.println("MESSAGE (귓←" + id + ") " + p[2]);
		}
	}
}


