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
		private volatile boolean fin;
		private User user;
		private String exBehavior;
		Selector selector;
		CharsetEncoder encoder;
		Charset charset;
		//결국 싱글턴으로 구성해야한다.
		
		public static ClientThread getInstance(Abortable abortable, String host, int port, User user) {
			if(clientThread == null) {
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
			this.fin = false;
			this.user = user;
			this.exBehavior = null;
			
			charset = Charset.forName("UTF-8");
			encoder = charset.newEncoder();
		}
		
		public void sendFinMessage(User user) throws IOException {
			String userKey = user.email +  "-" + user.provider;
			String msg = "ZocoChat://ask//" + userKey;
			sayToServer(msg);
		}
		
		//상대의 아이디를 알아야함..
		//언제 알거야? 채팅방들어갈떄? 채팅방 들어갈때 oppositeKey를 받는다.
		//채팅방에 정보에 이미 정보가 들어가있어도 상관없음.
		//한번더 질의했을때 문제점은 ?
		public void sendMessage(User user, String oppositeKey, String msgContent) throws IOException {
			String msg = "ZocoChat://message//id//"+ user.email + "//from//"+ user.chatId + "//to//" +user.chatId + "//" + msgContent;
			sayToServer(msg);
		}
		
		public void sendAskMessage(User user) throws IOException {
			String msg = "ZocoChat://ask//" + user.chatId;
			sayToServer(msg);
		}
		
		public void sendInitMessage(User user) throws IOException {
			String msg = "ZocoChat://init//" + user.chatId;
			sayToServer(msg);
		}
		
		public void tryToConnect() throws IOException, InterruptedException {
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
		public void sayToServer(String text) throws IOException {
			int len = client.write(encoder.encode(CharBuffer.wrap(text)));
			System.out.printf("[write :: text : %s / len : %d]\n", text, len);
		}

		@Override
		public void run() {
			while (!fin) {

				super.run();

				boolean done = false;
				
				Charset cs = Charset.forName("UTF-8");

				try {

					tryToConnect();
					sendAskMessage(user);

					ByteBuffer buffer = ByteBuffer.allocate(1024);
					
					//exbehave가 
					//sendAskMessage(user);

					while (!Thread.interrupted() && !abortable.isDone()
							&& !done) {

						selector.select(3000);

						Iterator<SelectionKey> iter = selector.selectedKeys()
								.iterator();
						
						if(exBehavior != null && exBehavior.equals("set")) {
							sendInitMessage(user);
						}
						
						while (!Thread.interrupted() && !abortable.isDone()
								&& !done && iter.hasNext()) {
							
							SelectionKey key = iter.next();
							if (key.isReadable()) {
								int len = client.read(buffer);
								if (len < 0) {
									System.out.println("Client :: server closed");
									done = true;
									break;
								} else if (len == 0) {
									continue;
								}
								buffer.flip();

								CharBuffer cb = cs.decode(buffer);

								System.out.printf("From Server : ");
								sb.setLength(0);
								while (cb.hasRemaining()) {
									sb.append(cb.get());
								}
								//String msg = "ZocoChat://set//";
								//ZocoChat://message//from//id//to//id//message contents
								//nio가 오히려 더 느리게 만들수 도 있다는 생각이 든다.  미친듯이 와일문을 돌고 있으므로...
								String msg = sb.toString();
								System.out.println(msg);
								String[] splited = msg.split("//");
								String behavior = splited[1].trim();
								
								if(behavior.equals("set")||behavior.equals("ask")) {
									String[] ipAndPort=splited[2].split(":");
									String ip = ipAndPort[0].trim();
									int port = Integer.parseInt(ipAndPort[1]);
									this.host = ip;
									this.port = port;
									tryToConnect();
									if(behavior.equals("set")) {
										sendInitMessage(user);
									}
								}
								

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
					if (client != null) {
						try {
							client.socket().close();
							client.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}

					System.out.println("Client :: done");
				}

			}

		}
	}
