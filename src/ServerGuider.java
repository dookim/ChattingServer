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
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class ServerGuider {
	
	public Map<String,ZocoServer> clientServerMap;

	//�대씪�댁뼵�몃뒗 �먯떊��硫붿떆吏�� 蹂대궪���곌껐���딆뼱議뚮뒗吏�遊먯빞��
	
	// �대뼡 梨꾨꼸���대뼡 IO瑜������덈뒗吏��뚮젮二쇰뒗 �대옒��Seelctor)
	Selector selector;

	// �쒓� �꾩넚��
	Charset charset = Charset.forName("UTF-8");
	CharsetEncoder encoder = charset.newEncoder();
	CharsetDecoder decoder = charset.newDecoder();
	private Map<Integer, Integer> portMap;
	private StringBuilder sb = new StringBuilder();
	private List<ZocoServer> servers;
	private String ip;
	
	public void addServer(ZocoServer server) {
		servers.add(server);
	}
	
	
	//hashmap�섍릿��
	public ServerGuider(String ip, int port, Map<Integer, Integer> portMap) throws IOException {
		// TODO Auto-generated constructor stub`
		this.portMap = portMap;
		this.servers = new ArrayList<ZocoServer>();
		this.ip = ip;
		//u should make config file

		
		this.clientServerMap = new ConcurrentHashMap<String, ZocoServer>();
		//吏�슦吏��덈뒗�� �⑤뒗 硫붿떆吏�뒗 �대뼸寃��좉쾬�멸� ?
		// Selector瑜��앹꽦 �⑸땲��
		selector = Selector.open();

		// ServerSocket����쓳�섎뒗 ServerSocketChannel���앹꽦, �꾩쭅 諛붿씤�⑹� �덈맖
		ServerSocketChannel channel = ServerSocketChannel.open();
		// �쒕쾭 �뚯폆 �앹꽦
		ServerSocket socket = channel.socket();

		SocketAddress addr = new InetSocketAddress(port);
		// �뚯폆���대떦 �ы듃濡�諛붿씤��
		socket.bind(addr);

		// Non-Blocking �곹깭濡�留뚮벉
		channel.configureBlocking(false);

		// 諛붿씤�⑸맂 ServerSocketChannel��Selector���깅줉 �⑸땲��
		channel.register(selector, SelectionKey.OP_ACCEPT);

		System.out.println("---- Client���묒냽��湲곕떎由쎈땲��.. ----");
		
	}
	
	public void run() {
		//�ш린��臾쇱뼱蹂대㈃ �대떦 �뺣낫瑜�由ы꽩�댁���
		//�ㅼ젣濡�而ㅻ꽖�섏쓣 留븐쑝硫�Server�먯꽌 �낅뜲�댄듃 �섎룄濡앺븳��
		//�쇱젙 �쒓컙��吏�궃�꾩뿉 client���묒냽 �댁뾾�ㅻ㈃ �쒓굅�섎룄濡��쒕떎.
		
		// SocketChannel��蹂�닔瑜�誘몃━ 留뚮뱾���〓땲��
		int socketOps = SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE;

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

						System.out.println("## socket accepted : " + socketChannel);

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
								//read하는 부분까지.
								//제대로 읽지ㄴ를 못함
								while (cb.hasRemaining()) {
									sb.append(cb.get());
								}
								// ZocoChat://ask//emailProvider
								// ZocoChat://init//emailProvider
								// ZocoChat://message//from//id//to//id//message contents
								// ZocoChat://fin//emailProvider
								String rcvdMsg = sb.toString();
								System.out.println(rcvdMsg);
								String[] splited = rcvdMsg.split("//");
								String behavior = splited[1].trim();

								// �뚯씠釉붿뿉 媛깆떊
								if (behavior.equals("ask")) {
									//get ip and port
									String toMsg = makeResponseMsg(splited[2].trim());
									socketChannel.write(encoder.encode(CharBuffer.wrap(toMsg)));
									
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
	
	private String makeResponseMsg(String emailProvider) {
		String msg = "ZocoChat://set//";
		if(clientServerMap.containsKey(emailProvider)) {
			return msg + ip + ":" +portMap.get(clientServerMap.get(emailProvider).socket.getLocalPort());
		} else {
			Collections.sort(servers);
			ZocoServer server = servers.get(0);
			System.out.println("client list : " + server.clientSockTable.size());
			return msg + ip + ":" +portMap.get(server.socket.getLocalPort());
		}
		
	}
	
	public static void main(String[] args) throws IOException {
		//port瑜��섎닠�쇳븳��
		//main thread���ㅻⅨ �ㅻ젅�쒖쓽 �듭떊���대뼸寃��섎뒗媛�?
		//block�섏엳�ㅺ� 
		//read config
		String ip = null;
		int guiderPort = -1;
		HashMap<Integer, Integer> portMap = new HashMap<Integer, Integer>();
		BufferedReader br = new BufferedReader(new FileReader(new File("config.cfg")));
		String temp;
		while((temp = br.readLine()) != null) {
			String[] splited = temp.split(":");
			String category = splited[0];
			if(category.equals("ip")) {
				ip = splited[1].trim();
			} else if(category.equals("server-port")) {
				String[] ports = splited[1].split("-");
				portMap.put(Integer.parseInt(ports[0]), Integer.parseInt(ports[1]));
			} else if(category.equals("guider-port")) {
				guiderPort = Integer.parseInt(splited[1]);
			}
		}
		br.close();
		
		Set<Integer> ports = portMap.keySet();
		ServerGuider guider = new ServerGuider(ip, guiderPort, portMap);
		
		for(Integer port : ports) {
			ZocoServer server = new ZocoServer(guider,port);
			server.start();
		}	
		guider.run();
		
	}
}
