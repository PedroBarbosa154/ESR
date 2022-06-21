package OttNode;

import Bootstrapper.Bootstrapper;
import utils.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.*;
import java.io.*;
import java.util.*;
import javax.swing.Timer;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OttNode {

    public static final String STREAMER_IP = "10.0.0.10";
    public static final int STREAM_UDP_PORT = 25000;
    public static final int OTT_NODE_NEIGHBOR_PORT = 9090;
    public static final int OTT_NODE_BOOTSTRAPPER_PORT = 9000;

    static ServerSocket server_socket;
    static NodeType type;
    static boolean consuming;  // is consuming stream or not
    static Set<InetAddress> addresses_to_redirect_stream;  // addresses of neighbors on the stream path
    static InetAddress incoming_ip;  // address of the neighbor where the stream is coming from
    static int jumps;  // number of jumps from the streamer to the node
    static ConcurrentMap<String, Neighbor> neighbors;  // key is neighbor IP address
    static BlockingQueue<Packet> recv_packet_queue;  // message syntax:  SRC_IP_ADDRESS:message
    static StreamWindow stream_window;

    enum NodeType {
        SIMPLE, CLIENT, STREAMER
    }

    static void start_procedure(NodeType type) {
        try {
            OttNode.server_socket = new ServerSocket(OttNode.OTT_NODE_NEIGHBOR_PORT);
            OttNode.type = type;
            OttNode.consuming = false;
            OttNode.addresses_to_redirect_stream = new HashSet<>();
            OttNode.incoming_ip = null;
            OttNode.jumps = Integer.MAX_VALUE;
            OttNode.neighbors = new ConcurrentHashMap<>();
            OttNode.recv_packet_queue = new LinkedBlockingQueue<>();
            OttNode.stream_window = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void get_neighbors_from_bootstrapper() {
        try {
            // Start connection with bootstrapper
            Socket socket = new Socket(Bootstrapper.BOOTSTRAPPER_IP, Bootstrapper.BOOTSTRAPPER_PORT);
            System.out.println("[DEBUG] Connected to bootstrapper with IP " + Bootstrapper.BOOTSTRAPPER_IP + " on PORT " + Bootstrapper.BOOTSTRAPPER_PORT);

            DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            // Get neighbors from bootstrapper
            int nr_neighbors = input.readInt();
            for (int i = 0; i < nr_neighbors; i++) {
                String neighbor = input.readUTF();
                String neighbor_name = neighbor.split(":", 2)[0];
                String neighbor_ip = neighbor.split(":", 2)[1];

                OttNode.neighbors.put(neighbor_ip, new Neighbor(neighbor_name));
            }
            System.out.println("[DEBUG] Neighbors: " + OttNode.neighbors.toString());

            // Close connection with bootstrapper
            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        try {
            // Get node type from arguments. Simple by default
            NodeType node_type = NodeType.SIMPLE;
            if (args.length > 0) {
                if (args[0].equals("-streamer")) node_type = NodeType.STREAMER;
                else if (args[0].equals("-client")) node_type = NodeType.CLIENT;
            }

            // Start client variables
            OttNode.start_procedure(node_type);
            OttNode.get_neighbors_from_bootstrapper();

            // Thread to control incoming connections from neighbors
            Thread ott_node_listener_thread = new Thread(new OttNodeListener(OttNode.server_socket));
            ott_node_listener_thread.start();

            // Thread to control incoming stream packets on the UDP Socket
            Thread ott_node_udp_listener_thread = new Thread(new OttNodeUdpListener());
            ott_node_udp_listener_thread.start();

            // Thread to wait for bootstrapper permission to start flooding
            if (OttNode.type == NodeType.STREAMER) {
                Thread bootstrapper_waiter = new Thread(new BootstrapperWaiter());
                bootstrapper_waiter.start();
            }
            // Thread to wait for input from stdin
            else if (OttNode.type == NodeType.CLIENT) {
                Thread client_stdin_reader = new Thread(new ClientStdInReader());
                client_stdin_reader.start();
            }

            // Try to establish connection with neighbors
            for (String neighbor_ip : OttNode.neighbors.keySet()) {
                // Try to reach the neighbor. If successful, register its connection
                try {
                    Socket neighbor_socket = new Socket(neighbor_ip, OttNode.OTT_NODE_NEIGHBOR_PORT);

                    register_neighbor_connection(neighbor_ip, neighbor_socket);

                } catch (IOException e) {
                    System.out.println("[DEBUG] Unable to connect to neighbor '" + OttNode.neighbors.get(neighbor_ip).getName() + "' with IP " + neighbor_ip + ". Neighbor is probably inactive.");
                }

            }

            // Control received messages queue
            Packet packet;
            try {
                while (true) {
                    packet = OttNode.recv_packet_queue.take();
                    PacketType packet_type = packet.getType();
                    //System.out.println("[DEBUG] Removed packet " + packet + " from packet queue.");

                    if (packet_type == PacketType.START_FLOODING && OttNode.type == NodeType.STREAMER) {
                        streamer_start_flooding();
                    }
                    else if (packet_type == PacketType.FLOODING) {
                        handle_flooding_packet(packet);
                    }
                    else if (packet_type == PacketType.CANCEL_STREAM_FLOW) {
                        //System.out.println("[DEBUG] Handling cancel stream flow packet: " + packet.toString());
                        OttNode.addresses_to_redirect_stream.remove(packet.getSrc_ip());
                    }
                    else if (packet_type == PacketType.CONFIRM_STREAM_FLOW) {
                        //System.out.println("[DEBUG] Handling confirm stream flow packet: " + packet.toString());
                        OttNode.addresses_to_redirect_stream.add(packet.getSrc_ip());
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /* Code used by all node types */
    static void register_neighbor_connection(String neighbor_ip, Socket neighbor_socket) {
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(neighbor_socket.getOutputStream()));
            oos.flush();
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(neighbor_socket.getInputStream()));

            // Thread to control outgoing packets to this neighbor
            Thread receiver_thread = new Thread(new OttNodeReceiver(neighbor_ip, ois));

            // Thread to control incoming packets from this neighbor
            Thread sender_thread = new Thread(new OttNodeSender(neighbor_ip, oos, neighbor_socket.getLocalAddress()));

            // Update neighbor socket information on neighbors map
            Neighbor neighbor = OttNode.neighbors.get(neighbor_ip);
            neighbor.register_established_connection(neighbor_socket, receiver_thread, sender_thread);

            // Start neighbor thread after updating information
            receiver_thread.start();
            sender_thread.start();

            System.out.println("[DEBUG] Connected to neighbor '" + neighbor.getName() + "' with IP " + neighbor_ip + " on PORT " + OttNode.OTT_NODE_NEIGHBOR_PORT);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    static class OttNodeReceiver implements Runnable {
        private final String neighbor_ip;
        private final ObjectInputStream ois;

        public OttNodeReceiver(String neighbor_ip, ObjectInputStream ois) {
            this.neighbor_ip = neighbor_ip;
            this.ois = ois;
        }

        public void run() {
            try {
                while (true) {
                    // Read from socket
                    Packet recv_packet = (Packet) ois.readObject();
                    System.out.println("[DEBUG] Received packet: " + recv_packet.toString());

                    // Add packet to received packet queue
                    OttNode.recv_packet_queue.put(recv_packet);
                }
            } catch (InterruptedException | IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    static class OttNodeSender implements Runnable {
        private final String neighbor_ip;
        private final ObjectOutputStream oos;
        private final InetAddress local_address;

        public OttNodeSender(String neighbor_ip, ObjectOutputStream oos, InetAddress local_address) {
            this.neighbor_ip = neighbor_ip;
            this.oos = oos;
            this.local_address = local_address;
        }

        public void run() {
            try {
                while (true) {
                    Packet send_packet = OttNode.neighbors.get(neighbor_ip).getSend_packet_queue().take();
                    send_packet.setSrc_ip(this.local_address);

                    oos.writeObject(send_packet);
                    oos.flush();

                    System.out.println("[DEBUG] Sent packet: " + send_packet.toString());
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }

    }

    static class BootstrapperWaiter implements Runnable {
        public void run() {
            try {
                // Wait for bootstrapper to connect
                ServerSocket server_socket = new ServerSocket(OttNode.OTT_NODE_BOOTSTRAPPER_PORT);
                Socket socket = server_socket.accept();

                // Once bootstrapper has connected, read packet from socket
                ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                Packet recv_packet = (Packet) ois.readObject();
                System.out.println("[DEBUG] Received packet: " + recv_packet.toString());

                // Add packet to received packet queue
                OttNode.recv_packet_queue.put(recv_packet);

                // Starts reading from stdin
                Thread streamer_stdin_reader = new Thread(new StreamerStdInReader());
                streamer_stdin_reader.start();

            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    static class ClientStdInReader implements Runnable {
        public void run() {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                while (true) {
                    String command = br.readLine();

                    switch (command) {
                        case "active":
                            OttNode.consuming = true;
                            OttNode.stream_window = new StreamWindow(VideoStream.VIDEO_FILENAME);
                            System.out.println("[DEBUG] Consuming status changed to active.");
                            break;
                        case "inactive":
                            OttNode.consuming = false;
                            OttNode.stream_window.close();
                            OttNode.stream_window = null;
                            System.out.println("[DEBUG] Consuming status changed to inactive.");
                            break;
                        default:
                            System.out.println("[ERROR] Unexpected value. Node status remains the same.");
                            break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class StreamerStdInReader implements Runnable {
        public void run() {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                while (true) {
                    String command = br.readLine();

                    switch (command) {
                        case "start_streaming":
                            Thread streaming_thread = new Thread(new OttNodeStreaming());
                            streaming_thread.start();
                            break;
                        default:
                            System.out.println("[ERROR] Unexpected value.");
                            break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class OttNodeStreaming implements Runnable, ActionListener {
        private DatagramSocket udp_socket;
        private VideoStream video_stream;
        private Timer timer;
        private Lock lock;
        private Condition next_frame_cond;

        public OttNodeStreaming() {
            try {
                this.udp_socket = new DatagramSocket();
                this.video_stream = new VideoStream(VideoStream.VIDEO_FILENAME);

                this.timer = new Timer(VideoStream.FRAME_PERIOD, this);
                this.timer.setInitialDelay(0);
                this.timer.setCoalesce(true);

                this.lock = new ReentrantLock();
                this.next_frame_cond = lock.newCondition();

            } catch (SocketException | FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                byte[] buffer = new byte[VideoStream.MAX_FRAME_SIZE];
                this.timer.start();

                while (video_stream.getFrame_nr() < VideoStream.VIDEO_LENGTH) {
                    // Get next frame from the video, as well as its size
                    int frame_length = this.video_stream.getNextFrame(buffer);

                    // Build an RTP utils.Packet containing the frame
                    int frame_number = video_stream.getFrame_nr();
                    RtpPacket rtp_packet = new RtpPacket(RtpPacket.MJPEG_TYPE, frame_number,
                            frame_number* VideoStream.FRAME_PERIOD, buffer, frame_length);

                    // Get the size of the packet to send
                    int packet_size = rtp_packet.getPacketSize();

                    // Get the packet bitstream
                    byte[] packet_bits = new byte[packet_size];
                    rtp_packet.getPacketBitstream(packet_bits);

                    // Send the packet over the UDP socket, for all the neighbors that follow the stream flow
                    for (InetAddress dst_address : OttNode.addresses_to_redirect_stream) {
                        DatagramPacket udp_packet = new DatagramPacket(packet_bits, packet_size, dst_address, OttNode.STREAM_UDP_PORT);
                        this.udp_socket.send(udp_packet);
                    }

                    // Wait for the timer to wake up
                    try {
                        this.lock.lock();
                        this.next_frame_cond.await();
                    } finally {
                        this.lock.unlock();
                    }                 }

                this.timer.stop();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }

        public void actionPerformed(ActionEvent actionEvent) {
            try {
                this.lock.lock();
                this.next_frame_cond.signal();
            } finally {
                this.lock.unlock();
            }        }
    }

    static void handle_flooding_packet(Packet recv_packet) {
        // Check packet distance to streamer
        int jumps = Integer.parseInt(recv_packet.getMessage());

        //System.out.println("[DEBUG] Handling flooding packet: " + recv_packet.toString());

        // If packet comes from a shorter path, update information and resend it to neighbors
        if (jumps < OttNode.jumps) {

            // Inform neighbor that stream flow isn't coming from him
            if (OttNode.incoming_ip != null) {
                Neighbor neighbor = OttNode.neighbors.get(OttNode.incoming_ip.getHostAddress());
                Packet old_packet = new Packet(PacketType.CANCEL_STREAM_FLOW, null, "");
                neighbor.getSend_packet_queue().add(old_packet);
            }

            // Inform neighbor that stream flow is coming from him
            InetAddress new_incoming_ip = recv_packet.getSrc_ip();
            Packet new_packet = new Packet(PacketType.CONFIRM_STREAM_FLOW, null, "");
            OttNode.neighbors.get(new_incoming_ip.getHostAddress()).getSend_packet_queue().add(new_packet);

            // Update information
            OttNode.jumps = jumps;
            OttNode.incoming_ip = new_incoming_ip;

            // Resend packet to neighbors except the one where it came from
            for (Map.Entry<String, Neighbor> entry : OttNode.neighbors.entrySet()) {
                try {
                    if (!entry.getKey().equals(recv_packet.getSrc_ip().getHostAddress())) {
                        Packet packet = new Packet(PacketType.FLOODING, null, Integer.toString(jumps+1));
                        entry.getValue().getSend_packet_queue().put(packet);
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("[DEBUG] Updated stream flow: Jumps = " + OttNode.jumps + " AND Incoming IP = " + OttNode.incoming_ip);
        }

    }

    /* Code used by streamer nodes */
    static void streamer_start_flooding() {
        OttNode.jumps = 0;
        OttNode.incoming_ip = null;
        OttNode.consuming = false;

        for (Neighbor neighbor : OttNode.neighbors.values()) {
            try {
                Packet packet = new Packet(PacketType.FLOODING, null, "1");
                neighbor.getSend_packet_queue().put(packet);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    /* Code used by client nodes */

}