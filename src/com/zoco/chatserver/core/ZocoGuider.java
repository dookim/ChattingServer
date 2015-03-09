package com.zoco.chatserver.core;
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

import com.zoco.chatserver.util.Constants;
import com.zoco.chatserver.util.ServerUtil;

//change all words into english 
/**
 * @author dookim
 * guider guide that client should be connected which server 
 * guider pick the smallest number of connection 
 */
public class ZocoGuider extends Thread {
	
	//it was shared by ZocoServer, so that's the reason why we use protected visibility
	protected Map<String,ZocoServer> clientServerMap;
	protected Selector selector;

	private Charset charset = Charset.forName("UTF-8");
	private CharsetEncoder encoder = charset.newEncoder();
	private Map<Integer, Integer> portMap;
	private StringBuilder sb = new StringBuilder();
	private List<ZocoServer> servers;
	private String ip;
	private ByteBuffer buff;
	
	//this is called by Zocoserver in their constructor
	/**
	 * @param server
	 * Guider have all of servers
	 * the reason why guider have servers is when each zocoserver communicate each other, they use zocoguider's infomation
	 * so, zocosguider's infomation is partially thread-safe
	 */
	protected void addServer(ZocoServer server) {
		servers.add(server);
	}
	
	/**
	 * @param the ip used by zocoguider
	 * @param port the port used by zocoguider
	 * @param portMap portMap maps the real port used in os to virtual port(public port, (public port is explicitly released to client)) 
	 * @throws IOException
	 */
	private ZocoGuider(String ip, int port, Map<Integer, Integer> portMap) throws IOException {
		// TODO Auto-generated constructor stub`
		this.portMap = portMap;
		this.servers = new ArrayList<ZocoServer>();
		this.ip = ip;
		
		this.clientServerMap = new ConcurrentHashMap<String, ZocoServer>();
		this.selector = Selector.open();

		ServerSocketChannel channel = ServerSocketChannel.open();
		ServerSocket socket = channel.socket();
		SocketAddress addr = new InetSocketAddress(port);
		socket.bind(addr);

		channel.configureBlocking(false);
		channel.register(selector, SelectionKey.OP_ACCEPT);
		buff = ByteBuffer.allocate(1024);
		System.out.println("---- ready to connect----");
		
	}

	/**
	 * if u call start method, guider consistently guide client where they should connect
	 */
	public void run() {
		int socketOps = SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE;
		try {
			while (selector.select() > 0) {

				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> iter = keys.iterator();

				while (iter.hasNext()) {
					SelectionKey selected = (SelectionKey) iter.next();
					iter.remove();

					SelectableChannel channel = selected.channel();
					if (channel instanceof ServerSocketChannel) {

						ServerSocketChannel serverChannel = (ServerSocketChannel) channel;
						SocketChannel socketChannel = serverChannel.accept();

						if (socketChannel == null) {
							System.out.println("## null server socket");
							continue;
						}

						System.out.println("## socket accepted : " + socketChannel);
						socketChannel.configureBlocking(false);
						socketChannel.register(selector, socketOps);

					} else {
						SocketChannel socketChannel = (SocketChannel) channel;
						buff.clear();

						if (selected.isConnectable()) {
							System.out.println("Client connection OK~");
							if (socketChannel.isConnectionPending()) {
								System.out.println("client connection is pended!");
								socketChannel.finishConnect();
							}
						}
						if (selected.isReadable()) {
							socketChannel.read(buff);
							if (buff.position() != 0) {
								buff.flip();
								CharBuffer cb = charset.decode(buff);
								sb.setLength(0);
		
								while (cb.hasRemaining()) {
									sb.append(cb.get());
								}

								String rcvdMsg = sb.toString();
								System.out.println(rcvdMsg);
								String[] splited = rcvdMsg.split("//");
								String behavior = splited[1].trim();

								if (behavior.equals("ask")) {
									//get ip and port
									String toMsg = makeResponseMsg(splited[2].trim());
									socketChannel.write(encoder.encode(CharBuffer.wrap(toMsg)));
									//close this socket!
									ServerUtil.closeChannel(socketChannel);
									
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
	/**
	 * @param chatId client's unique chatid
	 * @return response string which client will get
	 * if guider find user in clientservermap, guider just return response string where client was,
	 * however, if not, guider return zocoserver which has the smallest clients 
	 */
	private String makeResponseMsg(String chatId) {
		String msg =  Constants.PROTOCOL + Constants.BEHAVIOUR_SET + "//";
		if(clientServerMap.containsKey(chatId)) {
			return msg + ip + ":" +portMap.get(clientServerMap.get(chatId).getLocalPort());
		} else {
			Collections.sort(servers);
			ZocoServer server = servers.get(0);
			System.out.println("client list : " + server.clientSockTable.size());
			return msg + ip + ":" +portMap.get(server.getLocalPort());
		}
	}
	//to do additionally?
	public static void main(String[] args) throws IOException {

		String ip = null;
		int guiderPort = -1;
		HashMap<Integer, Integer> portMap = new HashMap<Integer, Integer>();
		//read config information from config.cfg
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
		
		//run guider and server
		Set<Integer> ports = portMap.keySet();
		ZocoGuider guider = new ZocoGuider(ip, guiderPort, portMap);
		
		for(Integer port : ports) {
			ZocoServer server = new ZocoServer(guider,port);
			server.start();
		}	
		guider.run();
		
	}
}
