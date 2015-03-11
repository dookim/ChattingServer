package com.zoco.chatserver.core;
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

import com.zoco.chatserver.util.Constants;
import com.zoco.chatserver.util.ServerUtil;


/**
 * 
 * @author dookim
 * ZocoServer actually accept client's connections and send data or receive data
 * when ZocoServer get some data from the client that the ZocoServer doesn't manage,
 * this Zocoserver send data to another Zocoserver which actullay manage the client
 */
public class ZocoServer extends Thread implements Comparable<ZocoServer> {
	
	protected ConcurrentLinkedQueue<ZocoMsg> messageListFromManager;
	protected BidiMap<String, SocketChannel> clientSockTable;
	
	private ZocoGuider guider;
	private Selector selector;
	private Charset charset = Charset.forName("UTF-8");
	private CharsetEncoder encoder = charset.newEncoder();	
	private Map<String, LinkedList<String>> messageList;
	private StringBuilder sb = new StringBuilder();
	private ServerSocket socket;
	private SocketChannel socketChannel;
	private ByteBuffer buff;
	
	/**
	 * 
	 * @param guider each zocoserver have guider, because in order to send data to another zocoserver, ther refer to guider's information
	 * @param port the port used by zocoserver
	 * @throws IOException
	 */
	protected ZocoServer(ZocoGuider guider, int port) throws IOException {
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
		buff = ByteBuffer.allocate(4096);

		System.out.println("---- ready to connect ----");
	}
	/**
	 * when this method was started by start method, this method's behaviour is divided into 4 types
	 * first, receive data from client, transform the data to another client
	 * if this zocoserver don't have another client, send data to another zocoserver which own another client.
	 * 
	 */
	public void run() {
		int socketOps = SelectionKey.OP_CONNECT | SelectionKey.OP_READ | SelectionKey.OP_WRITE;		

		try {
			while (selector.select() > 0) {

				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> iter = keys.iterator();
				// because it is writable!!
				
				while (messageListFromManager.size() > 0) {
					ZocoMsg zocoMsg = messageListFromManager.poll();
					String toId = zocoMsg.toId;
					String toMsg = zocoMsg.msg;
					sendMessage(toId, toMsg);
				}
				while (iter.hasNext()) {
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

						System.out.println("## socket accepted : " + socketChannel);
						socketChannel.configureBlocking(false);
						socketChannel.register(selector, socketOps);

					} else {
						SocketChannel socketChannel = null;
						try {
							socketChannel = (SocketChannel) channel;

							if (selected.isConnectable()) {
								System.out.println("Client OK~");
								if (socketChannel.isConnectionPending()) {
									System.out.println("Client connections is pended~");
									socketChannel.finishConnect();
								}
							}

							if (selected.isReadable()) {
								buff.clear();
								System.out.println("readable");
								int len=socketChannel.read(buff);
								buff.flip();
								if (len > 0) {
									System.out.println("buff.position() != 0");
									CharBuffer cb = charset.decode(buff);
									sb.setLength(0);
									while (cb.hasRemaining()) {
										sb.append(cb.get());
									}
									// ZocoChat://init//emailProvider//lastReceivedIndex
									// "ZocoChat://message//" + bookId + "//" + chattingIndex + "//" + System.currentTimeMillis() + "//" + user.email + "//" + user.chatId + "//" + oppositeChatId + "//" + msgContent;
									// ZocoChat://fin//emailProvider
									String rcvdMsg = sb.toString();
									System.out.println(rcvdMsg);
									String[] splited = rcvdMsg.split("//");
									String behavior = splited[1].trim();
									//저장을 먼저 생각해야 하나? 긁어오는 것부터 생각하자.
									if (behavior.equals(Constants.BEHAVIOUR_INIT)) {
										String id = splited[2].trim();
										int lastReceivedIndex = Integer.parseInt(splited[3].trim());
										clientSockTable.put(id, socketChannel);
										guider.clientServerMap.put(id, this);
										LinkedList<String> messages = messageList.get(id);
										if(lastReceivedIndex == -1) {
											if (messages != null) {
												Iterator<String> msgIter = messages.iterator();
												while (msgIter.hasNext()) {
													String msg = msgIter.next();
													Thread.sleep(5);
													socketChannel.write(encoder.encode(CharBuffer.wrap(msg)));
													msgIter.remove();
												}
											}
										}
										// app을 비정상적으로 종료시켰을때 메시지를 어떻게 보내는가냐.
									} else if (behavior.equals(Constants.BEHAVIOUR_MESSAGE)) {
										//"ZocoChat://message//bookId//lastReceivedIndex//chattingIndex//System.currentTimeMillis()//user.email//user.chatId//msgContent;
										String toId = splited[7].trim();
										String toMsg = Constants.PROTOCOL + Constants.BEHAVIOUR_MESSAGE + "//" 
												+ splited[2] + "//"
												+ -1 +"//"
												+ splited[3] + "//"
												+ splited[4] + "//"
												+ splited[5] + "//"
												+ splited[6] + "//"
												+ splited[8];
										sendMessage(toId, toMsg);
									} else if (behavior.equals(Constants.BEHAVIOUR_FIN)) {
										String id = splited[2].trim();
										clientSockTable.remove(id);
										guider.clientServerMap.remove(id);
									} else if(behavior.equals(Constants.BEHAVIOUR_CONFIRM)) {
										String toId = splited[4].trim();
										sendMessage(toId, rcvdMsg);
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
	/**
	 * 
	 * @param channel
	 * if client abnormally disconnect connection, server remove this channel
	 */
	private void removeChannel(SocketChannel channel) {
		if (clientSockTable.inverseBidiMap().containsKey(channel)) {
			String key = clientSockTable.inverseBidiMap().get(channel);
			// gc를 위해 hashmap 값초기화
			clientSockTable.put(key, null);
		}
		ServerUtil.closeChannel(channel);
	}

	/**
	 * 
	 * @param toId the id which zocoserver want to send this data
	 * @param toMsg 
	 * @throws CharacterCodingException
	 */
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
	
	
	/**
	 * 
	 * @param toId
	 * @param msg
	 * if client doens't connect server, server save this message into linkedlist
	 */
	private void addMessage(String toId, String msg) {
		if (!clientSockTable.containsKey(toId)) {
			throw new IllegalStateException("cannot find this user(" + toId+ ")");
		}

		LinkedList<String> messages = messageList.get(toId);
		if (messages == null) {
			messages = new LinkedList<String>();
		}
		messages.add(msg);
		messageList.put(toId, messages);
	}
	
	/**
	 * 
	 * @param toId
	 * @param msg
	 * replace the last message of linked list with this msg
	 */
	private void replaceMessageAtLastIndex(String toId, String msg) {
		if (!clientSockTable.containsKey(toId)) {
			throw new IllegalStateException("cannot find this user(" + toId+ ")");
		}
		LinkedList<String> messages = messageList.get(toId);
		if (messages == null) {
			messages = new LinkedList<String>();
			messages.add(msg);
		} else {
			if(messages.size() == 0) {
				messages.add(msg);
			} else {
				messages.set(messages.size() - 1, msg);
			}
			
		}
		messageList.put(toId, messages);
	}

	public int compareTo(ZocoServer o) {
		// TODO Auto-generated method stub
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
	
	/**
	 * 
	 * @return return this channel's port 
	 */
	protected int getLocalPort() {
		return socket.getLocalPort();
	}
	

}
