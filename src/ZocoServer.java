import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

public class ZocoServer extends Thread implements Comparable<ZocoServer> {

	Selector selector;

	Charset charset = Charset.forName("UTF-8");
	CharsetEncoder encoder = charset.newEncoder();

	public BidiMap<String, SocketChannel> clientSockTable;
	private Map<String, LinkedList<String>> messageList;
	public ConcurrentLinkedQueue<ZocoMsg> messageListFromManager;
	private StringBuilder sb = new StringBuilder();
	public ServerSocket socket;
	public ServerGuider guider;
	private SocketChannel socketChannel;

	public ZocoServer(ServerGuider guider, int port) throws IOException {
		this.guider = guider;
		guider.addServer(this);

		clientSockTable = new DualHashBidiMap<String, SocketChannel>();
		messageList = new HashMap<String, LinkedList<String>>();
		messageListFromManager = new ConcurrentLinkedQueue<ZocoMsg>();
		selector = Selector.open();

		ServerSocketChannel channel = ServerSocketChannel.open();
		socket = channel.socket();

		SocketAddress addr = new InetSocketAddress(port);
		socket.bind(addr);

		channel.configureBlocking(false);
		channel.register(selector, SelectionKey.OP_ACCEPT);

		System.out.println("---- ready to connect ----");
	}

	// 나름의 주석을 열심히 달아야 할듯함
	public void run() {
		int socketOps = SelectionKey.OP_CONNECT | SelectionKey.OP_READ
				| SelectionKey.OP_WRITE;

		ByteBuffer buff = null;

		try {
			// 아래는 도데체 언제 발생하는거지?
			while (selector.select() > 0) {
				System.out.println("selector.select() > 0");

				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> iter = keys.iterator();
				// because it is writable!!
				// 이거 하는 도중에 접속한다면 ? 메시지 로스일어남...이 아니라 메시지가 순차적이지 않음.채팅방내에선 순차적
				if (messageListFromManager.size() > 0) {
					while (messageListFromManager.size() > 0) {
						ZocoMsg zocoMsg = messageListFromManager.poll();
						String toId = zocoMsg.toId;
						String toMsg = zocoMsg.msg;
						sendMessage(toId, toMsg);
					}
				}
				while (iter.hasNext()) {
					System.out.println("iter!!");
					SelectionKey selected = (SelectionKey) iter.next();
					iter.remove();
					SelectableChannel channel = selected.channel();

					if (channel instanceof ServerSocketChannel) {

						ServerSocketChannel serverChannel = (ServerSocketChannel) channel;
						socketChannel = serverChannel.accept();

						if (socketChannel == null) {
							System.out.println("## null server socket");
							continue;
						}

						System.out.println("## socket accepted : "
								+ socketChannel);
						socketChannel.configureBlocking(false);
						socketChannel.register(selector, socketOps);

					} else {
						SocketChannel socketChannel = null;
						try {
							socketChannel = (SocketChannel) channel;
							buff = ByteBuffer.allocate(1024);

							if (selected.isConnectable()) {
								System.out.println("Client OK~");
								if (socketChannel.isConnectionPending()) {
									System.out
											.println("Client��쓽 �곌껐 �ㅼ젙��留덈Т由�以묒엯�덈떎~");
									socketChannel.finishConnect();
								}
							}

							if (selected.isReadable()) {
								System.out.println("readable");
								socketChannel.read(buff);
								int len = buff.position();
								if (len > 0) {
									System.out.println("buff.position() != 0");
									buff.clear();

									CharBuffer cb = charset.decode(buff);
									sb.setLength(0);

									while (cb.hasRemaining()) {
										sb.append(cb.get());
									}
									// ZocoChat://init//emailProvider
									// ZocoChat://message//email//doo871128//from//id//to//id//message
									// ZocoChat://fin//emailProvider
									String rcvdMsg = sb.toString();
									System.out.println(rcvdMsg);
									String[] splited = rcvdMsg.split("//");
									String behavior = splited[1].trim();

									if (behavior.equals("init")) {
										System.out.println("init!!!");
										String id = splited[2].trim();
										clientSockTable.put(id, socketChannel);
										guider.clientServerMap.put(id, this);
										LinkedList<String> messages = messageList
												.get(id);
										if (messages != null) {
											Iterator<String> msgIter = messages
													.iterator();
											while (msgIter.hasNext()) {
												String msg = msgIter.next();
												Thread.sleep(5);
												socketChannel.write(encoder
														.encode(CharBuffer
																.wrap(msg)));
												msgIter.remove();
											}

										}
										// app을 비정상적으로 종료시켰을때 메시지를 어떻게 보내는가냐.
									} else if (behavior.equals("message")) {
										String toId = splited[7].trim();
										String toMsg = "ZocoChat://"
												+ splited[5] + "//"
												+ splited[3] + "//"
												+ splited[8];
										sendMessage(toId, toMsg);
									} else if (behavior.equals("fin")) {
										String id = splited[2].trim();
										clientSockTable.remove(id);
										guider.clientServerMap.remove(id);
									}

									while (messageListFromManager.size() > 0) {
										ZocoMsg zocoMsg = messageListFromManager
												.poll();
										String toId = zocoMsg.toId;
										String toMsg = zocoMsg.msg;
										sendMessage(toId, toMsg);
									}
								} else if (len <= 0) {
									throw new IOException();
								}
							}
						} catch (IOException e) {
							System.out.println("remove channel");
							removeChannel(socketChannel);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

				}
			}
		} catch (ClosedChannelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CharacterCodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("io exception");
			e.printStackTrace();

		}

	}

	private void removeChannel(SocketChannel channel) {
		if (clientSockTable.inverseBidiMap().containsKey(channel)) {
			String key = clientSockTable.inverseBidiMap().get(channel);
			// gc를 위해 hashmap 값초기화
			clientSockTable.put(key, null);
		}
		ServerUtil.closeChannel(channel);
	}

	// 어차피 지정된 행동을 해야함
	// if socket is abnormally closed?? -> 알 방법이 없는가? -> 없는 듯함 (함수를 더써보자)
	// if socket is normally closed? -> 이럴일이 존재하는가 ? 어차피 소켓은 계속 붇어있을건데??? ->
	// 이럴일은 없다.
	// 썼는데 이미 클로즈 되어있다면? 그때가 문제점임.

	private void sendMessage(String toId, String toMsg)
			throws CharacterCodingException {
		if (clientSockTable.containsKey(toId)) {
			SocketChannel socketChannel = clientSockTable.get(toId);
			if (socketChannel != null) {
				System.out.println("toMsg : " + toMsg);
				try {
					socketChannel.write(encoder.encode(CharBuffer.wrap(toMsg)));
					replaceMessageAtLastIndex(toId, toMsg);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					System.out.println("cannot send msg");
					removeChannel(socketChannel);
					addMessage(toId, toMsg);
					return;
				}
			}
			// 이미 해당 클라이언트가 없다면!!
			// 메시지의 순차가 바뀔수 있나?
			else {
				addMessage(toId, toMsg);
			}
		} else {
			if (guider.clientServerMap.containsKey(toId)) {
				ZocoServer server = guider.clientServerMap.get(toId);
				server.messageListFromManager.add(new ZocoMsg(toId, toMsg));
			} else {
			}

		}
	}

	// 안전성을 보장하기 위해서. 하지만 대부분 msg저장할때 id가 있으므로 runtime 에러는 나타내지 않음
	private void addMessage(String toId, String msg) {
		if (!clientSockTable.containsKey(toId)) {
			throw new IllegalStateException("cannot find this user(" + toId
					+ ")");
		}

		LinkedList<String> messages = messageList.get(toId);
		if (messages == null) {
			messages = new LinkedList<String>();
		}
		messages.add(msg);
		messageList.put(toId, messages);
	}

	private void replaceMessageAtLastIndex(String toId, String msg) {
		if (!clientSockTable.containsKey(toId)) {
			throw new IllegalStateException("cannot find this user(" + toId
					+ ")");
		}

		LinkedList<String> messages = messageList.get(toId);
		if (messages == null) {
			messages = new LinkedList<String>();
			messages.add(msg);
		} else {
			messages.set(messages.size() - 1, msg);
		}
		messageList.put(toId, messages);
	}

	public int compareTo(ZocoServer o) {
		// TODO Auto-generated method stub
		// �ㅻ쫫李�
		return clientSockTable.size() - o.clientSockTable.size();
	}

	class ZocoMsg {
		String toId;
		String msg;

		public ZocoMsg(String toId, String msg) {
			// TODO Auto-generated constructor stub
			this.toId = toId;
			this.msg = msg;
		}
	}

}
