import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {

	// 현재 접속 중인 사용자 ID 목록
	private static Set<String> names = new HashSet<>();

	// 현재 접속 중인 모든 클라이언트 출력 스트림(브로드캐스트용)
	private static Set<PrintWriter> writers = new HashSet<>();

	// 계정 정보 저장 파일
	private static File accountFile = new File("accounts.dat");

	// 계정 정보(id → [salt, hash, name, email])
	private static Map<String, String[]> accounts = new HashMap<>();

	// 유저별 출력 스트림(귓속말 처리용)
	private static Map<String, PrintWriter> userWriters = new HashMap<>();


	public static void main(String[] args) throws Exception {
		System.out.println("The chat server is running...");

		// 서버 시작 시 계정 정보 로드
		loadAccounts();

		ExecutorService pool = Executors.newFixedThreadPool(500);

		// 59001 포트에서 서버 실행
		try (ServerSocket listener = new ServerSocket(59001)) {
			while (true) {
				// 접속될 때마다 새로운 Handler 스레드 실행
				pool.execute(new Handler(listener.accept()));
			}
		}
	}


	// ============================
	//    PASSWORD HASH 함수들
	// ============================

	// 바이트 배열 → 16진수 문자열 변환
	private static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) sb.append(String.format("%02x", b));
		return sb.toString();
	}

	// 랜덤 솔트 생성 (16바이트)
	private static String generateSalt() {
		byte[] salt = new byte[16];
		new SecureRandom().nextBytes(salt);
		return bytesToHex(salt);
	}

	// SHA-256 해시 계산 (salt + password)
	private static String hashPassword(String password, String salt) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(salt.getBytes("UTF-8"));
		byte[] hashed = md.digest(password.getBytes("UTF-8"));
		return bytesToHex(hashed);
	}


	// ============================
	//    계정 파일에서 로드
	// ============================

	private static void loadAccounts() {
		accounts.clear();

		if (!accountFile.exists()) {
			System.out.println("accounts.dat 없음 — 새로 시작");
			return;
		}

		try (BufferedReader br = new BufferedReader(new FileReader(accountFile))) {
			String line;

			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) continue;

				// 저장되는 형식:
				// id salt:hash name email
				String[] tok = line.split("\\s+");

				// 저장 형식이 맞는지 확인
				if (tok.length >= 4 && tok[1].contains(":")) {
					String id = tok[0];

					String[] sh = tok[1].split(":");
					String salt = sh[0];
					String hash = sh[1];

					String name = tok[2];
					String email = tok[3];

					accounts.put(id, new String[]{salt, hash, name, email});
				} else {
					// 잘못된(구버전) 비밀번호 형식은 무시
					System.out.println("⚠ 구버전 비밀번호 포맷 발견 — 무시됨: " + line);
				}
			}

			System.out.println("accounts loaded: " + accounts.size());
		} catch (Exception e) {
			System.out.println("계정 파일 로드 실패: " + e);
		}
	}


	// ============================
	//    계정 저장 (추가)
	// ============================

	private static synchronized void saveAccount(String id, String salt, String hash, String name, String email) {
		try (PrintWriter writer = new PrintWriter(new FileWriter(accountFile, true))) {
			writer.println(id + " " + salt + ":" + hash + " " + name + " " + email);
		} catch (IOException e) {
			System.out.println("계정 저장 실패: " + e);
		}
	}


	// ============================
	//         클라이언트 핸들러
	// ============================

	private static class Handler implements Runnable {

		private String id;         // 로그인한 사용자 ID
		private Socket socket;     // 소켓
		private Scanner in;        // 입력 스트림
		private PrintWriter out;   // 출력 스트림

		public Handler(Socket socket) {
			this.socket = socket;
		}

		public void run() {
			try {
				in = new Scanner(socket.getInputStream());
				out = new PrintWriter(socket.getOutputStream(), true);

				// 클라이언트에게 로그인 요구
				out.println("LOGIN");

				while (true) {
					if (!in.hasNextLine()) return;

					String line = in.nextLine().trim();
					if (line.isEmpty()) continue;

					// ---------------- CHECKID ----------------
					// 아이디 중복 확인 요청
					if (line.startsWith("CHECKID ")) {
						String checkId = line.substring(8).trim();
						boolean used = accounts.containsKey(checkId);
						out.println(used ? "IDUSED" : "IDOK");
						continue;
					}

					// ---------------- REGISTER ----------------
					// 회원가입 처리
					if (line.startsWith("REGISTER ")) {
						String[] p = line.split(" ");
						if (p.length >= 5) {

							String newId = p[1];
							String pw = p[2];
							String name = p[3];
							String email = p[4];

							synchronized (accounts) {
								// 이미 존재하는 ID인지 확인
								if (accounts.containsKey(newId)) {
									out.println("REGFAIL DuplicateID");
								} else {
									// 솔트 생성 후 비밀번호 해시 저장
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
					// 로그인 처리
					if (line.startsWith("LOGIN ")) {
						String[] parts = line.split(" ");
						if (parts.length == 3) {
							String loginId = parts[1];
							String loginPw = parts[2];

							// ID 존재 여부 확인
							if (!accounts.containsKey(loginId)) {
								out.println("NEEDREGISTER");
								continue;
							}

							String[] acc = accounts.get(loginId);
							String salt = acc[0];
							String storedHash = acc[1];

							// 입력된 비밀번호 해시 계산
							String inputHash = hashPassword(loginPw, salt);

							if (!storedHash.equals(inputHash)) {
								out.println("LOGINFAIL");
								continue;
							}

							// 이미 로그인 중인 사용자인지 확인
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

					// 회원가입 화면에서 취소 누른 경우
					if (line.equals("CANCELREGISTER")) {
						out.println("LOGIN");
						continue;
					}
				}

				// ============================
				// 로그인 성공 후 처리
				// ============================

				synchronized (names) {
					names.add(id);
				}

				userWriters.put(id, out);
				writers.add(out);

				// 클라이언트에 로그인 성공 알림
				out.println("NAMEACCEPTED " + id);

				// 전체 사용자에게 입장 메시지 브로드캐스트
				for (PrintWriter w : writers)
					w.println("MESSAGE " + id + " has joined");

				// ============================
				//       채팅 루프
				// ============================
				while (true) {
					if (!in.hasNextLine()) break;

					String msg = in.nextLine().trim();

					// 로그아웃 처리
					if (msg.equals("LOGOUT")) {
						out.println("BYE");
						break;
					}

					// 귓속말 (/w 사용자 메시지)
					if (msg.startsWith("/w ")) {
						handleWhisper(msg);
						continue;
					}

					// 일반 메시지 브로드캐스트
					for (PrintWriter w : writers)
						w.println("MESSAGE " + id + ": " + msg);
				}

			} catch (Exception e) {
				System.out.println(e);

			} finally {
				// 종료 처리
				if (id != null) {
					names.remove(id);
					writers.remove(out);
					userWriters.remove(id);

					// 전체 사용자에게 퇴장 메시지 브로드캐스트
					for (PrintWriter w : writers)
						w.println("MESSAGE " + id + " has left");
				}

				try { socket.close(); } catch (IOException e) {}
			}
		}

		// ============================
		//      귓속말 처리 함수
		// ============================
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

			// 발신자에게 표시
			out.println("MESSAGE (귓→" + p[1] + ") " + p[2]);
			// 수신자에게 전달
			target.println("MESSAGE (귓←" + id + ") " + p[2]);
		}
	}
}


