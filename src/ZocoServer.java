import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ZocoServer extends Thread implements Comparable<ZocoServer> {

	// �대뼡 梨꾨꼸���대뼡 IO瑜������덈뒗吏��뚮젮二쇰뒗 �대옒��Seelctor)
	Selector selector;

	// �쒓� �꾩넚��
	Charset charset = Charset.forName("UTF-8");
	CharsetEncoder encoder = charset.newEncoder();

	public Map<String, SocketChannel> clientSockTable;
	private Map<String, LinkedList<String>> messageList;
	public ConcurrentLinkedQueue<ZocoMsg> messageListFromManager;
	private StringBuilder sb = new StringBuilder();
	public ServerSocket socket;
	public ServerGuider guider;

	public ZocoServer(ServerGuider guider, int port) throws IOException {
		this.guider = guider;
		guider.addServer(this);

		clientSockTable = new ConcurrentHashMap<String, SocketChannel>();
		messageList = new HashMap<String, LinkedList<String>>();
		messageListFromManager = new ConcurrentLinkedQueue<ZocoMsg>();
		// Selector瑜��앹꽦 �⑸땲��
		selector = Selector.open();

		// ServerSocket����쓳�섎뒗 ServerSocketChannel���앹꽦, �꾩쭅 諛붿씤�⑹� �덈맖
		ServerSocketChannel channel = ServerSocketChannel.open();
		// �쒕쾭 �뚯폆 �앹꽦
		socket = channel.socket();

		SocketAddress addr = new InetSocketAddress(port);
		// �뚯폆���대떦 �ы듃濡�諛붿씤��
		socket.bind(addr);

		// Non-Blocking �곹깭濡�留뚮벉
		channel.configureBlocking(false);

		// 諛붿씤�⑸맂 ServerSocketChannel��Selector���깅줉 �⑸땲��
		channel.register(selector, SelectionKey.OP_ACCEPT);

		System.out.println("---- Client���묒냽��湲곕떎由쎈땲��.. ----");
	}

	// �대떦 �뚯폆����긽 �ш린���곌껐�섏엳�ㅺ퀬媛�젙�섍퀬 �쒕떎.
	// accept�섎뒗 怨쇱젙留덉� nonblock�섏꽌 肄붾뱶媛�援됱옣��蹂듭옟��

	public void run() {
		// SocketChannel��蹂�닔瑜�誘몃━ 留뚮뱾���〓땲��
		int socketOps = SelectionKey.OP_CONNECT | SelectionKey.OP_READ
				| SelectionKey.OP_WRITE;

		ByteBuffer buff = null;

		// �앹꽦���쒕쾭�뚯폆梨꾨꼸����빐 accept �곹깭 �쇰븣 �뚮젮�щ씪怨�selector���깅줉 �쒗궓 ��
		// �대깽�멸� �쇱뼱�좊븣 源뚯� 湲곕떎由쎈땲�� �덈줈���대씪�댁뼵�멸� �묒냽�섎㈃ seletor��
		// 誘몃━ �깅줉 �덈뜕 SeerverSocketChannel���대깽�멸� 諛쒖깮�덉쑝誘�줈 select 硫붿냼�쒖뿉��
		// 1���뚮젮以띾땲�� 利�Selector��媛먯����대깽�멸� �덈떎硫�

		try {
			while (selector.select() > 0) {

				// �꾩옱 selector���깅줉��梨꾨꼸���숈옉���쇰굹�쇰룄 �ㅽ뻾 �섎뒗 寃쎌슦 洹�梨꾨꼸�ㅼ쓣 SelectionKey��
				// Set��異붽� �⑸땲�� �꾨옒�먯꽌���좏깮��梨꾨꼸�ㅼ쓽 �ㅻ� �살뒿�덈떎. 利��대떦 IO����빐 �깅줉��
				// �볦� 梨꾨꼸���ㅻ� �삳뒗 寃곷땲��
				Set keys = selector.selectedKeys();
				Iterator iter = keys.iterator();

				while (iter.hasNext()) {
					SelectionKey selected = (SelectionKey) iter.next();
					// �꾩옱 泥섎━�섎뒗 SelectionKey��Set�먯꽌 �쒓굅 �⑸땲��
					iter.remove();

					// channel()���꾩옱 �섍퀬 �덈뒗 �숈옉(�쎄린, �곌린)����븳 �뚯븙���섍린 �꾪븳 寃곷땲��
					SelectableChannel channel = selected.channel();
					if(messageListFromManager.size() > 0) {
						while (messageListFromManager.size() > 0) {
							//�섑븳����msg��
							ZocoMsg zocoMsg = messageListFromManager.poll();
							String toId = zocoMsg.toId;
							String toMsg = zocoMsg.msg;
							sendMessage(toId, toMsg);
						}
					}
					else {
						if (channel instanceof ServerSocketChannel) {

							// ServerSocketChannel�대씪硫�accept()瑜��몄텧�댁꽌
							// �묒냽 �붿껌���댁삩 �곷�諛��뚯폆怨��곌껐 �����덈뒗 SocketChannel���살뒿�덈떎.
							ServerSocketChannel serverChannel = (ServerSocketChannel) channel;
							SocketChannel socketChannel = serverChannel.accept();

							// �꾩떆�먯쓽 ServerSocketChannel��Non-Blocking IO濡��ㅼ젙 �섏뼱
							// �덉뒿�덈떎.
							// �닿쾬���뱀옣 �묒냽���놁뼱��釉붾줈���섏� �딄퀬 諛붾줈 null���섏�誘�줈
							// 泥댄듃 �댁빞 �⑸땲��
							if (socketChannel == null) {
								System.out.println("## null server socket");
								continue;
							}

							System.out.println("## socket accepted : "
									+ socketChannel);

							// �살뼱吏��뚯폆��釉붾줈���뚯폆�대�濡�Non-Blocking IO �곹깭濡��ㅼ젙 �⑸땲��
							socketChannel.configureBlocking(false);

							// �뚯폆 梨꾨꼸��Selector���깅줉
							socketChannel.register(selector, socketOps);

						} else {
							// �쇰컲 �뚯폆 梨꾨꼸��寃쎌슦 �대떦 梨꾨꼸���살뼱�몃떎.
							SocketChannel socketChannel = (SocketChannel) channel;
							buff = ByteBuffer.allocate(1024);

							// �뚯폆 梨꾨꼸���됰룞��寃�궗�댁꽌 洹몄뿉 ��쓳�섎뒗 �묒뾽����
							if (selected.isConnectable()) {
								System.out.println("Client��쓽 �곌껐 �ㅼ젙 OK~");
								if (socketChannel.isConnectionPending()) {
									System.out.println("Client��쓽 �곌껐 �ㅼ젙��留덈Т由�以묒엯�덈떎~");
									socketChannel.finishConnect();
								}
							}
							// �쎄린 �붿껌 �대씪硫�
							if (selected.isReadable()) {
								// �뚯폆 梨꾨꼸濡��곗씠�곕� �쎌뼱 �ㅼ엯�덈떎.

								socketChannel.read(buff);

								// �곗씠�곌� �덈떎硫�
								if (buff.position() != 0) {
									buff.clear();

									// Non-Blocking Mode�대�濡��곗씠�곌� 紐⑤몢 �꾨떖�좊븣 源뚯� 湲곕떎由�
									CharBuffer cb = charset.decode(buff);
									sb.setLength(0);
									
									while (cb.hasRemaining()) {
										sb.append(cb.get());
									}
									// ZocoChat://init//emailProvider
									// ZocoChat://message//from//id//to//id//message
									// ZocoChat://fin//emailProvider
									String rcvdMsg = sb.toString();
									System.out.println(rcvdMsg);
									String[] splited = rcvdMsg.split("//");
									String behavior = splited[1].trim();

									// �뚯씠釉붿뿉 媛깆떊
									if (behavior.equals("init")) {
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
												socketChannel.write(encoder
														.encode(CharBuffer
																.wrap(msg)));
												msgIter.remove();
											}
										}
									//"ZocoChat://message//id//"+ user.email + "//from//"+ user.chatId + "//to//" +user.chatId + "//" + msgContent;
									} else if (behavior.equals("message")) {
										String toId = splited[7].trim();
										String toMsg = splited[3] + "//"+ splited[8];
										sendMessage(toId, toMsg);
									} else if(behavior.equals("fin")) {
										String id = splited[2].trim();
										clientSockTable.remove(id);
										guider.clientServerMap.remove(id);
									}

									while (messageListFromManager.size() > 0) {
										//�섑븳����msg��
										ZocoMsg zocoMsg = messageListFromManager.poll();
										String toId = zocoMsg.toId;
										String toMsg = zocoMsg.msg;
										sendMessage(toId, toMsg);
									}
								}
							}
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
			e.printStackTrace();
		}

	}

	private void sendMessage(String toId, String toMsg)
			throws CharacterCodingException, IOException {
		if (clientSockTable.containsKey(toId)) {
			SocketChannel sock = clientSockTable.get(toId);
			if (sock != null) {
				System.out.println("toMsg : " + toMsg);
				sock.write(encoder.encode(CharBuffer.wrap(toMsg)));
			} else {
				LinkedList<String> messages = messageList.get(toId);
				if (messages == null) {
					messages = new LinkedList<String>();
				}
				messages.add(toMsg);
				messageList.put(toId, messages);
			}
		}
		// key�먯껜媛��녿떎硫�,�ㅻⅨ �쒕쾭濡�硫붿떆吏�� 蹂대궡�쇳븿.
		else {
			if (guider.clientServerMap.containsKey(toId)) {
				ZocoServer server = guider.clientServerMap.get(toId);
				server.messageListFromManager.add(new ZocoMsg(toId, toMsg));
			} else {
				// �몃�濡�硫붿떆吏�諛쒖넚�댁빞��.
			}

		}
	}

	public int compareTo(ZocoServer o) {
		// TODO Auto-generated method stub
		// �ㅻ쫫李�
		return clientSockTable.size() - o.clientSockTable.size();
	}

	// ZocoMsg queue留뚮뱾湲�

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
