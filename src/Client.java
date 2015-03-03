import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;

/** 
 * 
 */
public class Client {

	private Abortable abortable = new Abortable();
	private ClientThread clientThread;
	static User user = new User("doo871128@gmail.com", "hufs", "facebook","1");
	

	/**
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		Client client = new Client();
		client.start("127.0.0.1", 7999);
		

		Thread.sleep(500);

		BufferedReader reader = new BufferedReader(new InputStreamReader(
				System.in));

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

	/**
	 * 
	 * @param text
	 * @throws IOException
	 */
	public void sayToServer(String text) throws IOException {
		clientThread.sayToServer(text);
	}

	/** 
     * 
     */
	
}
