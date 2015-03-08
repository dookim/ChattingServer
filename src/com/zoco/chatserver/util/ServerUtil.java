package com.zoco.chatserver.util;
import java.io.IOException;
import java.nio.channels.SocketChannel;


public class ServerUtil {
	
	public static void closeChannel(SocketChannel channel) {
		try {
			channel.socket().close();
			channel.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			// e.printStackTrace();
			return;
		}
	}

}
