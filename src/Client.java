import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;

/** 
 * 
 */
public class Client {

    private Abortable abortable = new Abortable();
    private ClientThread clientThread;

    /** 
     *  
     * @param args 
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception {

        Client client = new Client();
        //이미 이때부터 리슨하고있었고
        client.start("127.0.0.1", 8000);

        Thread.sleep(500);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        //사용자의 계속적인 input을 받기 위한 while문이다.
        while (true) {
            String line = reader.readLine();

            if (line.equals("quit"))
                break;
            //어디서 받지?
            try {
                client.sayToServer(line);
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }

        }

        client.stop();

        System.out.println("BYE");
    }

    /** 
     * start client 
     *  
     * @param host 
     * @param port 
     */
    public void start(String host, int port) {

        //일단 abortable은 무시하자.
        abortable.init();
        
        //null이거나 살아있지 않다면 start!
        if (clientThread == null || !clientThread.isAlive()) {
            clientThread = new ClientThread(abortable, host, port);
            clientThread.start();
        }
    }

    /** 
     * stop client 
     */
    public void stop() {

        abortable.done = true;

        if (clientThread != null && clientThread.isAlive()) {
            clientThread.interrupt();
        }

    }

    /** 
     *  
     * @param text 
     * @throws IOException 
     */
    public void sayToServer(String text) throws IOException {
        clientThread.sayToServer(text);
    }

    /** 
     * 
     */
    public class ClientThread extends Thread {

        private Abortable abortable;
        private String host;
        private int port;
        private SocketChannel client;

        /** 
         * @param abortable 
         * @param host 
         * @param port 
         */
        public ClientThread(Abortable abortable, String host, int port) {
            this.abortable = abortable;
            this.host = host;
            this.port = port;
        }
        
        
        //client가 결국 이걸 호출하겠지
        /** 
         *  
         * @param text 
         * @throws IOException  
         */
        public void sayToServer(String text) throws IOException {
            int len = client.write(ByteBuffer.wrap(text.getBytes()));
            System.out.printf("[write :: text : %s / len : %d]\n", text, len);
        }
        //결국 이부분을 이해하면 다 이해하는 거다.
        @Override
        public void run() {
            super.run();

            boolean done = false;
            Selector selector = null;
            Charset cs = Charset.forName("UTF-8");

            try {

                System.out.println("Client :: started");

                client = SocketChannel.open();
                client.configureBlocking(false);
                client.connect(new InetSocketAddress(host, port));

                selector = Selector.open();
                client.register(selector, SelectionKey.OP_READ);

                while (!Thread.interrupted() && !abortable.isDone() && !client.finishConnect()) {
                    Thread.sleep(10);
                }

                System.out.println("Client :: connected");

                ByteBuffer buffer = ByteBuffer.allocate(1024);

                while (!Thread.interrupted() && !abortable.isDone() && !done) {

                    selector.select(3000);

                    Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
                    while (!Thread.interrupted() && !abortable.isDone() && !done && iter.hasNext()) {
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
                            
                            //0보다 클때 limit과 클라이언트
                            buffer.flip();      
                            
                            CharBuffer cb = cs.decode(buffer);

                            System.out.printf("From Server : ");
                            
                            while (cb.hasRemaining()) {
                                System.out.printf("%c", cb.get());
                            }
                            System.out.println();

                            buffer.compact();
                        }
                    }
                }

            } catch (Exception e) {
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
