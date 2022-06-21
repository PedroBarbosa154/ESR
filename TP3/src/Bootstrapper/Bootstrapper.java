package Bootstrapper;

import OttNode.OttNode;
import utils.Packet;
import utils.PacketType;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bootstrapper {

    public static final String BOOTSTRAPPER_IP = "10.0.0.10";
    public static final int BOOTSTRAPPER_PORT = 8080;
    public static String TOPOLOGY_FILE;
    public static String HOSTS_FILE;

    public static void main(String[] args) {
        Bootstrapper.TOPOLOGY_FILE = "../" + args[0] + "_topo.txt";
        Bootstrapper.HOSTS_FILE = "../" + args[0] + "_hosts.txt";

        try {
            // Prepare bootstrapper
            Map<InetAddress, String> hosts = parseHosts();
            Topology topology = new Topology(TOPOLOGY_FILE);
            List<Thread> threads = new ArrayList<>();

            // Create ServerSocket
            ServerSocket ss = new ServerSocket(BOOTSTRAPPER_PORT);

            // Wait for all required nodes to connect
            int total_required_nodes = topology.getRequiredNodes().size();

            for (int i = 0; i < total_required_nodes; i++) {
                Socket socket = ss.accept();
                Thread bootstrapper_worker = new Thread (new BootstrapperWorker(socket, hosts, topology));
                bootstrapper_worker.start();

                threads.add(bootstrapper_worker);
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Tell streamer to start flooding
            boolean success = false;
            while (!success) {
                success = start_flooding();
                Thread.sleep(100);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    static Map<InetAddress, String> parseHosts() {
        Map<InetAddress, String > hosts = new HashMap<>();

        try {
            List<String> lines = Files.readAllLines(Paths.get(HOSTS_FILE), StandardCharsets.UTF_8);

            for (String line : lines) {
                String[] splitted_line = line.split("\\s+",2);
                InetAddress host_ip = InetAddress.getByName(splitted_line[0]);
                String host_name = splitted_line[1];
                hosts.put(host_ip, host_name);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return hosts;
    }

    static boolean start_flooding() {
        try {
            // Send message to streamer to start flooding the network
            Socket streamer_socket = new Socket(OttNode.STREAMER_IP, OttNode.OTT_NODE_BOOTSTRAPPER_PORT);

            ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(streamer_socket.getOutputStream()));
            Packet packet = new Packet(PacketType.START_FLOODING, InetAddress.getByName(Bootstrapper.BOOTSTRAPPER_IP), "");
            output.writeObject(packet);
            output.flush();

            System.out.println("[DEBUG] Sent packet: " + packet.toString());

            return true;
        } catch (IOException e) {
            System.out.println("[ERROR] Streamer isn't listening yet.");
            return false;
        }
    }
}

class BootstrapperWorker implements Runnable {
    private final Socket socket;
    private final DataOutputStream dos;
    private Map<InetAddress, String> hosts;
    private Topology topology;

    public BootstrapperWorker(Socket socket, Map<InetAddress, String> hosts, Topology topology) throws IOException {
        this.socket = socket;
        this.dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.hosts = hosts;
        this.topology = topology;
    }

    public void run() {
        try {
            InetSocketAddress socket_address = (InetSocketAddress) socket.getRemoteSocketAddress();
            InetAddress host_ip = socket_address.getAddress();

            String host_name = this.hosts.get(host_ip);

            List<String> neighbors = this.topology.getOverlay().get(host_name);
            dos.writeInt(neighbors.size());
            dos.flush();
            for (String neighbor_ip : neighbors) {
                dos.writeUTF(this.hosts.get(InetAddress.getByName(neighbor_ip)) + ":" + neighbor_ip);
                dos.flush();
            }

            this.topology.removeRequiredNode(host_name);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}