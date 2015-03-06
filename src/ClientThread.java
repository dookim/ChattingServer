import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Iterator;

public class ClientThread extends Thread {

	public static ClientThread clientThread;
	private Abortable abortable;
	private String host;
	private int port;
	private SocketChannel client;
	private StringBuilder sb;
	private User user;
	private Selector selector;
	private CharsetEncoder encoder;
	private Charset charset;
	
	//non blocking으로 할이유가 없음
	public static ClientThread getInstance(Abortable abortable, String host,
			int port, User user) {
		if (clientThread == null) {
			clientThread = new ClientThread(abortable, host, port, user);
		}
		return clientThread;
	}

	/**
	 * 
	 * @param abortable
	 * @param host
	 * @param port
	 */
	private ClientThread(Abortable abortable, String host, int port, User user) {
		this.abortable = abortable;
		this.host = host;
		this.port = port;
		this.sb = new StringBuilder();
		this.user = user;

		charset = Charset.forName("UTF-8");
		encoder = charset.newEncoder();
	}

	public void sendFinMessage(User user) throws IOException {
		String msg = "ZocoChat://fin//" + user.chatId;
		sayToServer(msg);
	}

	
	//시간을 넣어야 함.
	public void sendMessage(User user, String oppositeChatId, int bookId, int chattingIndex, String msgContent)
			throws IOException {
		String msg = "ZocoChat://message//" + bookId + "//" + chattingIndex + "//" + System.currentTimeMillis() + "//" + user.email + "//" + user.chatId + "//" + oppositeChatId + "//" + msgContent;
		sayToServer(msg);
	}

	private void sendAskMessage(User user) throws IOException {
		String msg = "ZocoChat://ask//" + user.chatId;
		sayToServer(msg);
	}
	//초기버전은 무조건 -1로 lastReceived를 채워준다.
	//하위 호환성을 위해서 -1을 덧붙여 보내준다.
	
	private void sendInitMessage(User user, int lastReceivedIndex) throws IOException {
		String msg = "ZocoChat://init//" + user.chatId + "//" + lastReceivedIndex;
		sayToServer(msg);
	}
	
	//client입장에서 호출해야할 메서드
	//그러므로 public
	 public void sendConfirmMessage(User user,int bookId, String oppositeChatId, int chattingIndex) throws IOException {
		String msg = "ZocoChat://confirm//"+ bookId + "//" + user.chatId + "//" + oppositeChatId + "//" + chattingIndex;
		sayToServer(msg);
	}
	


	private void tryToConnect() throws IOException, InterruptedException {
		System.out.println("Client :: started");

		client = SocketChannel.open();
		client.configureBlocking(false);
		client.connect(new InetSocketAddress(host, port));

		selector = Selector.open();
		client.register(selector, SelectionKey.OP_READ);

		while (!Thread.interrupted() && !abortable.isDone()
				&& !client.finishConnect()) {
			Thread.sleep(10);
		}

		System.out.println("Client :: connected");
	}

	/**
	 * 
	 * @param text
	 * @throws IOException
	 */
	private void sayToServer(String text) throws IOException {
		int len = client.write(encoder.encode(CharBuffer.wrap(text)));
		System.out.printf("[write :: text : %s / len : %d]\n", text, len);
	}

	@Override
	public void run() {

		super.run();

		boolean done = false;

		Charset cs = Charset.forName("UTF-8");

		try {

			tryToConnect();
			sendAskMessage(user);
			ByteBuffer buffer = ByteBuffer.allocate(4096);

			while (!Thread.interrupted() && !abortable.isDone() && !done) {
				System.out.println("hhhh");

				selector.select();

				Iterator<SelectionKey> iter = selector.selectedKeys().iterator();

				while (!Thread.interrupted() && !abortable.isDone() && !done && iter.hasNext()) {

					SelectionKey key = iter.next();
					if (key.isReadable()) {
						buffer.clear();
						int len = client.read(buffer);
						if (len < 0) {
							System.out.println("Client :: server closed");
							done = true;
							break;
						} else if (len == 0) {
							continue;
						}
						// i read this buffer, so i wanna get bytes from this buffer.
						buffer.flip();

						CharBuffer cb = cs.decode(buffer);

						System.out.printf("From Server : ");
						sb.setLength(0);
						while (cb.hasRemaining()) {
							sb.append(cb.get());
						}
						
						String msg = sb.toString();
						System.out.println(msg);
						
						String[] splited = msg.split("//");
						String behavior = null;
						try {
							behavior = splited[1].trim();
						} catch(ArrayIndexOutOfBoundsException e) {
							e.printStackTrace();
							continue;
						}
						

						if (behavior.equals("set") || behavior.equals("ask")) {
							close();
							String[] ipAndPort = splited[2].split(":");
							String ip = ipAndPort[0].trim();
							int port = Integer.parseInt(ipAndPort[1]);
							this.host = ip;
							this.port = port;
							tryToConnect();
							if (behavior.equals("set")) {
								sendInitMessage(user, -1);
							}
						//"ZocoChat://message//bookId//lastReceivedIndex//chattingIndex//System.currentTimeMillis()//user.email//user.chatId//msgContent;
						} 
						else if(behavior.equals("message")) {
							sendConfirmMessage(user, Integer.parseInt(splited[2]), splited[7], Integer.parseInt(splited[4]));
						}
						//갑자기 끊길 경우 대비
						System.out.println();
						buffer.compact();
					}
				}
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			close();
		}

	}
	
	private void close() {
		if (client != null) {
			try {
				client.socket().close();
				client.close();
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
		}

		System.out.println("Client :: done");
	}
}
