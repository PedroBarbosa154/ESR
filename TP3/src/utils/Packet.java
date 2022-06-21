package utils;

import java.io.Serializable;
import java.net.InetAddress;

public class Packet implements Serializable {
    private PacketType type;
    private InetAddress src_ip;
    private String message;

    public Packet(PacketType type, InetAddress src_ip, String message) {
        this.type = type;
        this.src_ip = src_ip;
        this.message = message;
    }

    public PacketType getType() {
        return type;
    }

    public InetAddress getSrc_ip() {
        return src_ip;
    }

    public void setSrc_ip(InetAddress src_ip) {
        this.src_ip = src_ip;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "utils.Packet{" +
                "type=" + type +
                ", src ip=" + src_ip.getHostAddress() +
                ", message=" + message +
                '}';
    }

}