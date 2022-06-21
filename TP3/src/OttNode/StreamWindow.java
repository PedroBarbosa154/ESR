package OttNode;

import utils.VideoStream;

import javax.swing.*;
import java.awt.*;

public class StreamWindow {
    private JFrame frame;
    private JPanel main_panel;
    private JLabel icon_label;
    private ImageIcon icon;

    public StreamWindow(String title) {
        frame = new JFrame(title);
        main_panel = new JPanel();
        icon_label = new JLabel();
        icon = null;

        icon_label.setIcon(null);
        main_panel.setLayout(null);
        main_panel.add(icon_label);
        icon_label.setBounds(0, 0, VideoStream.VIDEO_WIDTH, VideoStream.VIDEO_HEIGHT);

        frame.getContentPane().add(main_panel, BorderLayout.CENTER);
        frame.setSize(new Dimension(VideoStream.VIDEO_WIDTH+10, VideoStream.VIDEO_HEIGHT+10));
        frame.setVisible(true);
    }

    public void displayImage(Image image) {
        this.icon = new ImageIcon(image);
        this.icon_label.setIcon(icon);
    }

    public void close() {
        this.frame.dispose();
    }


}
