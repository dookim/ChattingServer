import java.io.BufferedReader;
import java.io.InputStreamReader;

/** 
 * 
 */
public class Client2 {

	private Abortable abortable = new Abortable();
	private ClientThread clientThread;
	static User user = new User("doo871128@gmail.com", "hufs", "facebook","1");

	/**
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		Client2 client = new Client2();
		client.start("127.0.0.1", 7999);
		Thread.sleep(500);

		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		int i = 1;

		while (true) {
			String line = reader.readLine();

			if (line.equals("quit"))
				break;
			try {
				//���� �˰ų�. Ȥ�� 
				client.clientThread.sendMessage(user, "2" ,1, i++, line);
			} catch (Exception e) {
				e.printStackTrace();
				break;
			}

		}

		client.stop();
		System.out.println("BYE");
	}

	/**
	 * start client
	 * 
	 * @param host
	 * @param port
	 */
	public void start(String host, int port) {

		abortable.init();

		if (clientThread == null || !clientThread.isAlive()) {
			
			clientThread = ClientThread.getInstance(abortable, host, port, user);
			clientThread.start();
		}
	}

	/**
	 * stop client
	 */
	public void stop() {

		abortable.done = true;

		if (clientThread != null && clientThread.isAlive()) {
			clientThread.interrupt();
		}

	}


	
}
