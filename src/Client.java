import java.io.BufferedReader;
import java.io.InputStreamReader;

/** 
 * 
 */
public class Client {

	private Abortable abortable = new Abortable();
	private ClientThread clientThread;
	static User user = new User("doo871128@gmail.com", "hufs", "facebook","2");
	/**
	 * 
	 * @param args
	 * @throws Exception
	 */
	//메시지는 순차적이다. 
	//순차적인 메시지를 받으면 된다.
	// 문제는 1 어디까지 읽었느냐 인거고
	//어디까지 읽었다라는건 어떻게 파악할까?
	
	public static void main(String[] args) throws Exception {

		Client client = new Client();
		client.start("127.0.0.1", 7999);
		Thread.sleep(500);

		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

		while (true) {
			String line = reader.readLine();

			if (line.equals("quit"))
				break;

			try {
				client.clientThread.sendMessage(user, "1", line);
				//client.sayToServer(line);
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
