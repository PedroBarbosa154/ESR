package utils;

import java.io.Serializable;

public class RtpPacket implements Serializable {

    public static final int HEADER_SIZE = 12;

    public static final int VERSION = 2;
    public static final int PADDING = 0;
    public static final int EXTENSION = 0;
    public static final int CC = 0;
    public static final int MARKER = 0;
    public static final int SSRC = 0;

    public static final int MJPEG_TYPE = 26;  // RTP payload type for MJPEG video

    /* -----------------------------------------------------------------
     * |-------1-------|-------2-------|-------3-------|-------4-------|
     * |0-1-2-3-4-5-6-7|0-1-2-3-4-5-6-7|0-1-2-3-4-5-6-7|0-1-2-3-4-5-6-7|
     * |Ver|P|X|  CC   |M|     PT      |       Sequence number         |
     * |                           Timestamp                           |
     * |                        SSRC Identifier                        |
     * -----------------------------------------------------------------
     */
    private byte[] header;  // Bitstream of the RTP header
    private int payload_size;  // Size of the RTP payload
    private byte[] payload;  // Bitstream of the RTP payload


    public RtpPacket(int payload_type, int frame_nr, int time, byte[] data, int data_length) {

        // Build and fill the header array with RTP header fields
        this.header = new byte[HEADER_SIZE];
        this.header[0] = (byte)((VERSION & 0x3) << 6 | (PADDING & 0x1) << 5 | (EXTENSION & 0x1) << 4 | CC & 0xF);
        this.header[1] = (byte)((MARKER & 0x1) << 7 | payload_type & 0x7F);

        this.header[2] = (byte)((frame_nr & 0xFF00) >> 8);
        this.header[3] = (byte)((frame_nr & 0x00FF));

        this.header[4] = (byte)((time & 0xFF000000) >> 24);
        this.header[5] = (byte)((time & 0x00FF0000) >> 16);
        this.header[6] = (byte)((time & 0x0000FF00) >> 8 );
        this.header[7] = (byte)( time & 0x000000FF       );

        this.header[8] =  (byte)((SSRC & 0xFF000000) >> 24);
        this.header[9] =  (byte)((SSRC & 0x00FF0000) >> 16);
        this.header[10] = (byte)((SSRC & 0x0000FF00) >> 8 );
        this.header[11] = (byte)( SSRC & 0xFF0000FF       );

        // Build and fill the payload bitstream
        this.payload_size = data_length;
        this.payload = new byte[data_length];
        System.arraycopy(data, 0, this.payload, 0, data_length);
    }

    public RtpPacket(byte[] packet, int packet_size) {

        if (packet_size >= HEADER_SIZE) {
            // Get the header bitstream
            this.header = new byte[HEADER_SIZE];
            System.arraycopy(packet, 0, this.header, 0, HEADER_SIZE);

            this.payload_size = packet_size - HEADER_SIZE;
            this.payload = new byte[this.payload_size];
            System.arraycopy(packet, HEADER_SIZE, this.payload, 0, this.payload_size);

        }
    }

    // Fills the given array with the packet header and return its size
    public int getHeader(byte[] data) {
        System.arraycopy(this.header, 0, data, 0, HEADER_SIZE);

        return HEADER_SIZE;
    }

    // Fills the given array with the packet payload and return its size
    public int getPayload(byte[] data) {
        System.arraycopy(this.payload, 0, data, 0, this.payload_size);

        return this.payload_size;
    }

    // Fills the given array with the packet header and payload and return its size
    public int getPacketBitstream(byte[] data) {
        System.arraycopy(this.header, 0, data, 0, HEADER_SIZE);
        System.arraycopy(this.payload, 0, data, HEADER_SIZE, this.payload_size);

        return HEADER_SIZE + this.payload_size;
    }

    // Returns the packet payload size
    public int getPayloadSize() {
        return this.payload_size;
    }

    // Returns the packet size
    public int getPacketSize() {
        return HEADER_SIZE + this.payload_size;
    }

    // utils.Packet header getters
    public int getVersion() {
        return ((this.header[0] & 0b11000000) >> 6);
    }

    public boolean hasPadding() {
        return ((this.header[0] & 0b00100000)) == 0b00100000;
    }

    public boolean hasExtension() {
        return ((this.header[0] & 0b00010000)) == 0b00010000;
    }

    public int getCC() {
        return ((this.header[0] & 0b00001111));
    }

    public boolean getMarker() {
        return ((this.header[1] & 0b10000000) == 0b10000000);
    }

    public int getPayloadType() {
        return ((this.header[1] & 0b01111111));
    }

    public int getSequenceNumber() {
        return ( ((this.header[2] << 8) & 0xFF00)
               |   this.header[3]       & 0x00FF);
    }

    public int getTimeStamp() {
        return ( ((this.header[4] << 24) & 0xFF000000)
               | ((this.header[5] << 16) & 0x00FF0000)
               | ((this.header[6] << 8)  & 0x0000FF00)
               |   this.header[7]        & 0x000000FF);
    }

    public int getSSRC() {
        return ( ((this.header[8] << 24) & 0xFF000000)
               | ((this.header[9] << 16) & 0x00FF0000)
               | ((this.header[10] << 8) & 0x0000FF00)
               |   this.header[11]       & 0x000000FF);
    }

    public String getHeaderString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("[RTP-Header] Version: ").append(getVersion())
                .append(", Padding: ").append(hasPadding())
                .append(", Extension: ").append(hasExtension())
                .append(", CC: ").append(getCC())
                .append(", Marker: ").append(getMarker())
                .append(", PayloadType: ").append(getPayloadType())
                .append(", SequenceNumber: ").append(getSequenceNumber())
                .append(", TimeStamp: ").append(getTimeStamp())
                .append(", SSRC: ").append(getSSRC()).append("\n");

        return sb.toString();
    }


}
