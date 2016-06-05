package UDPStopAndWait;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Created by Hatem on 01-Jun-16.
 *
 */
public class StopServer {
    public static final int PORT = 4900;
    private static final int BUFF_SIZE = 100;  //4 bytes for the frame number, 96 bytes for photo data
    private static final int FRAME_NUMBER_LENGTH = 4; //length of the sequence number
    private static int lastACK = -1;
    private static boolean isDuplicate = false;

    public static void main(String[] args) {
        try {
           int frameNumber = 0;
            byte lastPacket = 0;
            boolean isDone = false;

            ArrayList<Byte> photoBytes = new ArrayList<>();       //to contain the sent data from the client

            DatagramSocket socket = new DatagramSocket(PORT);
            System.out.println("UDPStopAndWait.StopServer started");
            while(true) {
                lastPacket = 0;
                int dataLength = 93;

                while (!isDone) {

                    byte[] sendBuffer = new byte[FRAME_NUMBER_LENGTH];
                    byte[] receiveBuffer = new byte[BUFF_SIZE];

                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, 0, BUFF_SIZE);
                    socket.receive(receivePacket);

                    ByteBuffer wrap1 = ByteBuffer.wrap(receiveBuffer);
                    int receivedFrameNumber = wrap1.getInt();          //the sequence number of a packet
                    lastPacket = wrap1.get(4);
                    System.out.println("last packet: "+lastPacket);
                    if(lastPacket == 1) {
                        isDone = true;
                        dataLength = wrap1.getShort(5);
                    }
                    if (receivedFrameNumber == lastACK) {
                        isDuplicate = true;
                    } else {
                        lastACK = receivedFrameNumber;
                    }
                    System.out.println("Data length: "+dataLength);
                    byte[] dataBuffer = new byte[receiveBuffer.length - 7];         //Taking only the data contents in the buffer
                    System.arraycopy(receiveBuffer, 7, dataBuffer, 0, dataLength);

                    if (!isDuplicate)
                        for(byte b : dataBuffer)
                            photoBytes.add(b);       //Adding the received packet data to save it in the list

                    ByteBuffer wrap = ByteBuffer.wrap(sendBuffer);
                    wrap.putInt(receivedFrameNumber);          //putting the sequence number in the buffer
                    sendBuffer = wrap.array();

                    DatagramPacket sendPacket = new DatagramPacket(sendBuffer, FRAME_NUMBER_LENGTH, receivePacket.getAddress(), receivePacket.getPort());
                    socket.send(sendPacket);
                    System.out.println("Stop And Wait Server sent ACK of frame number " + receivedFrameNumber);

                }
                System.out.println("2nd while loop started");
                byte[] photoArray = new byte[photoBytes.size()];

                for(int i = 0;i<photoArray.length;i++)
                    photoArray[i] = photoBytes.get(i);

                InputStream in = new ByteArrayInputStream(photoArray);
                BufferedImage bufferedImage = ImageIO.read(in);
                File newImage = new File("image\\stopAndwaitOut.png");
                ImageIO.write(bufferedImage, "png", newImage);

                isDone = false;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}