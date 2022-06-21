package OttNode;

import utils.Packet;

import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Neighbor {
    private String name;
    private Socket socket;
    private boolean active;
    private Thread receiver_thread;
    private Thread sender_thread;
    private BlockingQueue<Packet> send_packet_queue;

    public Neighbor(String name) {
        this.name = name;
        this.socket = null;
        this.active = false;
        this.receiver_thread = null;
        this.sender_thread = null;
        this.send_packet_queue = new LinkedBlockingQueue<>();
    }

    public void register_established_connection(Socket socket, Thread receiver_thread, Thread sender_thread) {
        this.socket = socket;
        this.active = true;
        this.receiver_thread = receiver_thread;
        this.sender_thread = sender_thread;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Thread getReceiver_thread() {
        return receiver_thread;
    }

    public void setReceiver_thread(Thread receiver_thread) {
        this.receiver_thread = receiver_thread;
    }

    public Thread getSender_thread() {
        return sender_thread;
    }

    public void setSender_thread(Thread sender_thread) {
        this.sender_thread = sender_thread;
    }

    public BlockingQueue<Packet> getSend_packet_queue() {
        return send_packet_queue;
    }

}
