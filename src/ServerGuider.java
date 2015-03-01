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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public class ServerGuider {
	
	public Map<String,ZocoServer> clientServerMap;

	//클라이언트는 자신이 메시지를 보낼때 연결이 끊어졌는지 봐야함.
	
	// 어떤 채널이 어떤 IO를 할 수 있는지 알려주는 클래스(Seelctor)
	Selector selector;

	// 한글 전송용
	Charset charset = Charset.forName("UTF-8");
	CharsetEncoder encoder = charset.newEncoder();
	private Map<Integer, Integer> portMap;
	private StringBuilder sb = new StringBuilder();
	private List<ZocoServer> servers;
	private String ip;
	
	public void addServer(ZocoServer server) {
		servers.add(server);
	}
	
	
	//hashmap넘긴다.
	public ServerGuider(String ip, int port, Map<Integer, Integer> portMap) throws IOException {
		// TODO Auto-generated constructor stub`
		this.portMap = portMap;
		this.servers = new ArrayList<ZocoServer>();
		this.ip = ip;
		//u should make config file

		
		this.clientServerMap = new ConcurrentHashMap<String, ZocoServer>();
		//지우지 안는다. 남는 메시지는 어떻게 할것인가 ?
		// Selector를 생성 합니다.
		selector = Selector.open();

		// ServerSocket에 대응하는 ServerSocketChannel을 생성, 아직 바인딩은 안됨
		ServerSocketChannel channel = ServerSocketChannel.open();
		// 서버 소켓 생성
		ServerSocket socket = channel.socket();

		SocketAddress addr = new InetSocketAddress(port);
		// 소켓을 해당 포트로 바인딩
		socket.bind(addr);

		// Non-Blocking 상태로 만듬
		channel.configureBlocking(false);

		// 바인딩된 ServerSocketChannel을 Selector에 등록 합니다.
		channel.register(selector, SelectionKey.OP_ACCEPT);

		System.out.println("---- Client의 접속을 기다립니다... ----");
		
	}
	
	public void run() {
		//여기다 물어보면 해당 정보를 리턴해준다.
		//실제로 커넥션을 맺으면 Server에서 업데이트 하도록한다.
		//일정 시간이 지난후에 client의 접속 이없다면 제거하도록 한다.
		
		// SocketChannel용 변수를 미리 만들어 둡니다.
		int socketOps = SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE;

		ByteBuffer buff = null;

		// 생성된 서버소켓채널에 대해 accept 상태 일때 알려달라고 selector에 등록 시킨 후
		// 이벤트가 일어날때 까지 기다립니다. 새로운 클라이언트가 접속하면 seletor는
		// 미리 등록 했던 SeerverSocketChannel에 이벤트가 발생했으므로 select 메소드에서
		// 1을 돌려줍니다. 즉 Selector에 감지된 이벤트가 있다면

		try {
			while (selector.select() > 0) {

				// 현재 selector에 등록된 채널에 동작이 라나라도 실행 되는 경우 그 채널들을 SelectionKey의
				// Set에 추가 합니다. 아래에서는 선택된 채널들의 키를 얻습니다. 즉 해당 IO에 대해 등록해
				// 놓은 채널의 키를 얻는 겁니다.
				Set keys = selector.selectedKeys();
				Iterator iter = keys.iterator();

				while (iter.hasNext()) {
					SelectionKey selected = (SelectionKey) iter.next();
					// 현재 처리하는 SelectionKey는 Set에서 제거 합니다.
					iter.remove();

					// channel()의 현재 하고 있는 동작(읽기, 쓰기)에 대한 파악을 하기 위한 겁니다.
					SelectableChannel channel = selected.channel();
					if (channel instanceof ServerSocketChannel) {

						// ServerSocketChannel이라면 accept()를 호출해서
						// 접속 요청을 해온 상대방 소켓과 연결 될 수 있는 SocketChannel을 얻습니다.
						ServerSocketChannel serverChannel = (ServerSocketChannel) channel;
						SocketChannel socketChannel = serverChannel.accept();

						// 현시점의 ServerSocketChannel은 Non-Blocking IO로 설정 되어
						// 있습니다.
						// 이것은 당장 접속이 없어도 블로킹 되지 않고 바로 null을 던지므로
						// 체트 해야 합니다.
						if (socketChannel == null) {
							System.out.println("## null server socket");
							continue;
						}

						System.out.println("## socket accepted : " + socketChannel);

						// 얻어진 소켓은 블로킹 소켓이므로 Non-Blocking IO 상태로 설정 합니다.
						socketChannel.configureBlocking(false);

						// 소켓 채널을 Selector에 등록
						socketChannel.register(selector, socketOps);

					} else {
						// 일반 소켓 채널인 경우 해당 채널을 얻어낸다.
						SocketChannel socketChannel = (SocketChannel) channel;
						buff = ByteBuffer.allocate(100);

						// 소켓 채널의 행동을 검사해서 그에 대응하는 작업을 함
						if (selected.isConnectable()) {
							System.out.println("Client와의 연결 설정 OK~");
							if (socketChannel.isConnectionPending()) {
								System.out.println("Client와의 연결 설정을 마무리 중입니다~");
								socketChannel.finishConnect();
							}
						}
						// 읽기 요청 이라면
						if (selected.isReadable()) {
							// 소켓 채널로 데이터를 읽어 들입니다.

							socketChannel.read(buff);

							// 데이터가 있다면
							if (buff.position() != 0) {
								buff.clear();
 
								// Non-Blocking Mode이므로 데이터가 모두 전달될때 까지 기다림
								sb.setLength(0);
								while (buff.hasRemaining()) {
									sb.append((char) buff.get());
								}
								// ZocoChat://ask//emailProvider
								// ZocoChat://init//emailProvider
								// ZocoChat://message//from//id//to//id//message contents
								// ZocoChat://fin//emailProvider
								String rcvdMsg = sb.toString();
								System.out.println(rcvdMsg);
								String[] splited = rcvdMsg.split("//");
								String behavior = splited[1].trim();

								// 테이블에 갱신
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
		//port를 나눠야한다.
		//main thread와 다른 스레드의 통신은 어떻게 하는가 ?
		//block되있다가 
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
