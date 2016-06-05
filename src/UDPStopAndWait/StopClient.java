package UDPStopAndWait;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by Hatem on 01-Jun-16.
 *
 */
public class StopClient {
    private static final int BUFF_SIZE = 100;  //4 bytes for the frame number, 96 bytes for photo data
    private static final int FRAME_NUMBER_LENGTH = 4;
    private static int frameNumber = 0;
    private static final int TIMEOUT_LIMIT = 2000;

    public static void main(String[] args) {

        try {
            byte[] receiveBuffer = new byte[FRAME_NUMBER_LENGTH];
            File image = new File("image\\skull.png");
            BufferedImage photo = ImageIO.read(image);
            System.out.println("image length" +image.length());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            ImageIO.write(photo, "png", baos);

            baos.flush();

            byte[] photoBytes = baos.toByteArray();
            baos.close();

            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(TIMEOUT_LIMIT);

            InetAddress IPAddress = InetAddress.getByName("localhost");

            int numberOfPackets = (int) Math.ceil(photoBytes.length / 93);   //Specifying the packets number in total

            System.out.println("Number of calculated packets packets "+numberOfPackets);
            System.out.println("Size of actual number of packets " + (numberOfPackets + 1));

            DatagramPacket[] allPackets = fillPackets(numberOfPackets, photoBytes, IPAddress);


            while(frameNumber < allPackets.length) {
                socket.send(allPackets[frameNumber]);
                System.out.println("Client sent packet "+frameNumber);
                try {
                    DatagramPacket receivedACK = new DatagramPacket(receiveBuffer, 0, FRAME_NUMBER_LENGTH);
                    socket.receive(receivedACK);

                    checkFrameNumber(receiveBuffer);

                    frameNumber++;
                } catch (SocketTimeoutException t) {
                    t.printStackTrace();
                }
            }
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static DatagramPacket[] fillPackets(int numberOfPackets, byte[] photoBytes, InetAddress IPAddress) {
        DatagramPacket[] packets = new DatagramPacket[numberOfPackets + 1];
        int startPosition;
        int dataLength = 93;

        //Filling the packet for every packet to be sent with the sequence number, whether it is the last packet and data contents
        for(int i = 0; i < packets.length ; i++) {
            byte[] sendBuffer = new byte[BUFF_SIZE];

            startPosition = i * dataLength;     //Incrementing the image data index

            ByteBuffer wrap = ByteBuffer.wrap(sendBuffer);
            wrap.putInt(i);           //putting the sequence number in the buffer
            if (dataLength > photoBytes.length - startPosition){     //If it is the last packet then make it "1" as true
                wrap.put((byte) 1);
                dataLength = photoBytes.length - startPosition -1;
                wrap.putShort((short)dataLength);
            }else {
                wrap.put((byte)0);
                wrap.putShort((short)0);  //If not the last packet then mark it with "0" as false
            }
            sendBuffer = wrap.array();
          System.out.println("Packet number: "+i +", start position: "+startPosition+ ",left: "+(photoBytes.length-startPosition)+", Data length: "+dataLength);
            System.arraycopy(photoBytes, startPosition, sendBuffer, 7, dataLength);

            packets[i] = new DatagramPacket(sendBuffer, 0, BUFF_SIZE, IPAddress, 4900);
        }
        return packets;
    }

    private static void checkFrameNumber(byte[] receiveBuffer){
        ByteBuffer wrap = ByteBuffer.wrap(receiveBuffer);
        int receivedFrameNumber = wrap.getInt();
        System.out.println("Client received ack "+receivedFrameNumber);
        if(receivedFrameNumber != frameNumber)
            frameNumber = receivedFrameNumber;
    }
}
