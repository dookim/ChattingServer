import java.io.*;
import java.net.*;

class EchoClient {
	public static void main(String[] args) throws IOException, InterruptedException {
		Socket sock = null;
		String ip = "127.0.0.1";
		int port = 8000;
		try {
			sock = new Socket(ip, port);
			System.out.println(sock + ": 연결됨");
			OutputStream toServer = sock.getOutputStream();
			InputStream fromServer = sock.getInputStream();

			byte[] buf = new byte[1024];
			int count;
			for(int i = 0; i < 1000; i++) {
				Thread.currentThread().sleep(1000);
				
				if(i == 0) {
					//ZocoChat://init//emailProvider
					//ZocoChat://messageTo//emailProvider//"message contents"
					String content ="ZocoChat://init//doo871128";
					toServer.write(content.getBytes());
					//count = fromServer.read(buf);
					//System.out.write(buf, 0, count);
					continue;
				}
				
				//ZocoChat://message//from//id//to//id//message contents
				//protocol바꿔야함 from도 넣어야할듯. 왜 ?
				//상대방이 모르나 ?  모른다 알수가없다. 따라서 보내야한다.
				String msg="ZocoChat://message//from//doo871128//to//doo871128//follow me";
				toServer.write(msg.getBytes());
				count = fromServer.read(buf);
				System.out.write(buf, 0, count);
				
			}
			toServer.close();
			
		} catch (IOException ex)

		{
			System.out.println("연결 종료 (" + ex + ")");
		} finally {
			try {
				if (sock != null)
					sock.close();
			} catch (IOException ex) {
			}
		}
	}
}