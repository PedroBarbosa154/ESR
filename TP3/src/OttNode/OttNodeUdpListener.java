package OttNode;

import utils.RtpPacket;
import utils.VideoStream;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class OttNodeUdpListener implements Runnable, ActionListener {
    private DatagramSocket socket;
    private Timer timer;
    private Lock lock;
    private Condition next_packet_cond;

    public OttNodeUdpListener() {
        try {
            this.socket = new DatagramSocket(OttNode.STREAM_UDP_PORT);
            // this.socket.setSoTimeout(5000);

            this.timer = new Timer(VideoStream.FRAME_PERIOD / 5, this);
            this.timer.setInitialDelay(0);
            this.timer.setCoalesce(true);

            this.lock = new ReentrantLock();
            this.next_packet_cond = lock.newCondition();

        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        byte[] buffer = new byte[VideoStream.MAX_FRAME_SIZE];
        this.timer.start();

        try {

            while (true) {
                // Create a DatagramPacket to receive data from the UDP socket
                DatagramPacket udp_packet = new DatagramPacket(buffer, buffer.length);

                // Receive a DatagramPacket from the socket
                this.socket.receive(udp_packet);

                // ---- RELAYING STREAM ----
                // Relay packet to neighbors that follow the stream flow
                for (InetAddress dst_address : OttNode.addresses_to_redirect_stream) {
                    DatagramPacket send_udp_packet = new DatagramPacket(udp_packet.getData(), udp_packet.getLength(), dst_address, OttNode.STREAM_UDP_PORT);
                    this.socket.send(send_udp_packet);
                }
                // ---- END RELAYING STREAM ----


                // ---- CONSUMING STREAM ----
                if (OttNode.consuming) {
                    // Create an RTP utils.Packet from the DatagramPacket received
                    RtpPacket rtp_packet = new RtpPacket(udp_packet.getData(), udp_packet.getLength());
                    //System.out.println("[DEBUG] Got RTP packet with TimeStamp "+rtp_packet.getTimeStamp()+" ms, of type "+rtp_packet.getPayloadType());

                    // Get the payload bitstream from the RTP utils.Packet
                    int payload_size = rtp_packet.getPayloadSize();
                    byte[] payload = new byte[payload_size];
                    rtp_packet.getPayload(payload);

                    // Get an Image object from the payload bitstream
                    Toolkit toolkit = Toolkit.getDefaultToolkit();
                    Image image = toolkit.createImage(payload, 0, payload_size);

                    // Display frame on the stream window
                    if (OttNode.stream_window != null)
                        OttNode.stream_window.displayImage(image);
                }
                // ---- END CONSUMING STREAM ----


                // Wait for the timer to wake up
                try {
                    this.lock.lock();
                    this.next_packet_cond.await();
                } finally {
                    this.lock.unlock();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void actionPerformed(ActionEvent actionEvent) {
        try {
            this.lock.lock();
            this.next_packet_cond.signal();
        } finally {
            this.lock.unlock();
        }
    }
}
