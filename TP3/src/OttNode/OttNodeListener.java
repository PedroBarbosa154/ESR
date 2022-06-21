package OttNode;

import OttNode.OttNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class OttNodeListener implements Runnable {

    private final ServerSocket server_socket;

    public OttNodeListener(ServerSocket server_socket) throws IOException {
        this.server_socket = server_socket;
    }

    public void run() {
        try {
            while (true) {
                // Waiting for a neighbor to connect
                Socket neighbor_socket = server_socket.accept();

                // Get neighbor IP address
                InetSocketAddress socket_address = (InetSocketAddress) neighbor_socket.getRemoteSocketAddress();
                String neighbor_ip = socket_address.getAddress().getHostAddress();

                OttNode.register_neighbor_connection(neighbor_ip, neighbor_socket);

            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
