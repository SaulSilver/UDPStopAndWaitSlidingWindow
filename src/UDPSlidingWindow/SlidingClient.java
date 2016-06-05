package UDPSlidingWindow;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by Hatem on 02-Jun-16.
 *
 */
public class SlidingClient {

    private static final int BUFF_SIZE = 100;  //4 bytes for the frame number, 96 bytes for photo data
    private static final int FRAME_NUMBER_LENGTH = 4;   //length of the sequence number
     private static final int TIMEOUT_LIMIT = 2000;      //2 secs
    private static int windowSize;
    private static int windowFirstPacket = 0;
    private static boolean shift = true;
    private static List<Integer> notACKPackets;
    private static int dataLength = 93;

    public static void main(String[] args) {
        try {
            byte[] receiveBuffer = new byte[FRAME_NUMBER_LENGTH];

            //Reading the image into bytes
            File image = new File("image\\danger.png");
            System.out.println("image length" +image.length());
            BufferedImage photo = ImageIO.read(image);

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

            //Specifying the sliding window size as the tenth of the image itself
            windowSize = allPackets.length / 10;

            while (windowSize == allPackets.length / 10) {               //checks if the it is the end of the image
                if(shift)
                    setSlidingWindowBuffer(windowFirstPacket, allPackets, photoBytes);

                //A loop to send all the packets within the window size
                for (int i = 0; i < windowSize; i++) {
                    socket.send(allPackets[notACKPackets.get(i)]);
                    ByteBuffer wrap = ByteBuffer.wrap(allPackets[notACKPackets.get(i)].getData());

                    System.out.println("Client sent packet "+wrap.getInt());
                }
                try {
                    for (int i = 0; i < windowSize; i++) {
                        DatagramPacket receivedACK = new DatagramPacket(receiveBuffer, 0, FRAME_NUMBER_LENGTH);
                        socket.receive(receivedACK);

                        checkFrameNumber(receiveBuffer);        //Checking the sequence number
                        receiveBuffer = new byte[FRAME_NUMBER_LENGTH];
                    }
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
        dataLength = 93;

        //Filling the packet for every packet to be sent with the sequence number, whether it is the last packet and data contents
        for(int i = 0; i < packets.length; i++) {
            byte[] sendBuffer = new byte[BUFF_SIZE];

            startPosition = i * dataLength;     //Incrementing the image data index

            ByteBuffer wrap = ByteBuffer.wrap(sendBuffer);
            wrap.putInt(i);           //putting the sequence number in the buffer
            if (dataLength > photoBytes.length - startPosition){     //If it is the last packet then make it "1" as true
                wrap.put((byte) 1);
                dataLength = photoBytes.length - startPosition ;
                wrap.putShort((short)dataLength);
            } else {
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

    /**
     * A method to check the received frame number if it is in the right order, if not then resend the the last acknowledged frame number +1
     *
     * @param receiveBuffer: buffer containing the data received from the last packet
     */
    private static void checkFrameNumber(byte[] receiveBuffer) {
        ByteBuffer wrap = ByteBuffer.wrap(receiveBuffer);
        int receivedFrameACK = wrap.getInt();
        System.out.println("client received ACK "+receivedFrameACK);
        if(notACKPackets.contains(receivedFrameACK)){
            if(receivedFrameACK == notACKPackets.get(0)) {
                if(notACKPackets.size() > 1)
                    windowFirstPacket = notACKPackets.get(1);
                else windowFirstPacket++;
                shift = true;
            }
            notACKPackets.remove((Integer) receivedFrameACK);
        }
    }

    private static void setSlidingWindowBuffer(int windowFirstPacket, DatagramPacket[] allPackets, byte[] photoBytes){
        if(allPackets.length == 1)              //If the image has one packet then set the window size to it
            windowSize = photoBytes.length;
        if(allPackets.length/10 <= 10)
            windowSize = allPackets.length;
        notACKPackets = new ArrayList<>();

        if(windowSize > photoBytes.length - windowFirstPacket)
            windowSize = allPackets.length - windowFirstPacket - 1;

        for(int i = 0; i < windowSize; i++){
            System.out.println("Step number "+i+" with window first packet "+windowFirstPacket);
            ByteBuffer wrap = ByteBuffer.wrap(allPackets[windowFirstPacket + i].getData());
            int frameNumber = wrap.getInt(0);
            notACKPackets.add(frameNumber);
        }
        shift = false;
    }

}
