import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

public class Example {
    public static void main(String[] args) throws IOException {
        InetSocketAddress address = new InetSocketAddress("localhost", 8080);
        SocketChannel socketChannel = SocketChannel.open(address);

        Charset charset = StandardCharsets.UTF_8;
        socketChannel.write(charset.encode(CharBuffer.wrap("hello world!")));

        ByteBuffer byteBuffer = ByteBuffer.allocate(81920);
        CharsetDecoder charsetDecoder = charset.newDecoder();
        CharBuffer charBuffer = CharBuffer.allocate(81920);

        while (socketChannel.read(byteBuffer) != -1 || byteBuffer.position() > 0) {
            byteBuffer.flip();
            charsetDecoder.decode(byteBuffer, charBuffer, true);
            charBuffer.flip();
            System.out.println(charBuffer);
            charBuffer.clear();
            byteBuffer.compact();
        }

        socketChannel.close();
    }
}
