package utils;

import java.io.*;

public class VideoStream {

    // TODO: ajustar isto para qualquer video e melhorar obtenção dos frames
    public static int MAX_FRAME_SIZE = 15000;
    public static int FRAME_PERIOD = 100; // Frame period, in ms
    public static int VIDEO_LENGTH = 500; // Length of the video, in frames
    public static String VIDEO_FILENAME = "../movie.Mjpeg";
    public static int VIDEO_HEIGHT = 288;
    public static int VIDEO_WIDTH = 384;

    private FileInputStream fis;
    private int frame_nr;

    public VideoStream(String filename) throws FileNotFoundException {
        this.fis = new FileInputStream(filename);
        this.frame_nr = 0;
    }

    public int getFrame_nr() {
        return frame_nr;
    }
    // TODO: reiniciar frame number quando chegar ao máximo
    public int getNextFrame(byte[] frame) throws IOException {

        // Read next frame length
        byte[] frame_length_array = new byte[5];
        fis.read(frame_length_array,0,5);
        int frame_length = Integer.parseInt(new String(frame_length_array));

        // Update frame number
        this.frame_nr++;

        return(fis.read(frame,0,frame_length));
    }
}