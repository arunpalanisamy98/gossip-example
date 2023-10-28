import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.io.Serializable;
import java.io.*;

public class GossipStarter {


    public static int serverPort = 48100;
    public static int nodeNumber = 0;
    public static int N = 25;
    public static Map<Integer,Integer> min = new HashMap<>();
    public static Map<Integer,Integer> max = new HashMap<>();
    public static GossipData nodeData = new GossipData(nodeNumber);
    public static void main(String[] args) throws Exception {

        serverPort = serverPort + Integer.parseInt(args[0]);
        nodeNumber = Integer.parseInt(args[0]);
        System.out.println(
                "Node " + nodeNumber + " starting up, listening at port " + (serverPort) + ".\n");
        nodeData.nodeNumber = nodeNumber;
        ConsoleLooper CL = new ConsoleLooper();
        GossipSender gossipSender = new GossipSender();
        nodeData.lowValue = nodeNumber;
        nodeData.requestServer = serverPort;
        nodeData.highValue = nodeNumber;
        nodeData.minNode = nodeNumber;
        nodeData.maxNode = nodeNumber;
        nodeData.localValue = nodeNumber;
        gossipSender.sendUpdateNodes(serverPort,nodeData);
        Thread t = new Thread(CL);
        t.start();

        boolean loopControl = true;

        try {
            DatagramSocket DGSocket = new DatagramSocket(serverPort);
            System.out.println("SERVER: Receive Buffer size: " + DGSocket.getReceiveBufferSize() + "\n");
            byte[] incomingData = new byte[1012];
            InetAddress IPAdress = InetAddress.getByName("localhost");

            while (loopControl) {
                DatagramPacket incomingPacket = new DatagramPacket(incomingData, incomingData.length);
                DGSocket.receive(incomingPacket);
                byte[] data = incomingPacket.getData();
                ByteArrayInputStream in = new ByteArrayInputStream(data);
                ObjectInputStream is = new ObjectInputStream(in);
                GossipData gossipObj = (GossipData) is.readObject();

                if (gossipObj.userString.indexOf("stopserver") > -1) {
                    System.out.println("SERVER: Stopping UDP listner now. \n");
                    loopControl = false;
                }
                new GossipWorker(gossipObj).start();
            }
        } catch (Exception e) {
            //e.printStackTrace();
        }

    }
}

class GossipWorker extends Thread {

    GossipData gossipData;

    GossipWorker(GossipData gossipData) {
        this.gossipData = gossipData;
    }

    public void run() {
        try{
        if(gossipData.type.equals("req")){
            if(gossipData.userString.equals("p")){
                GossipData response = new GossipData(GossipStarter.nodeNumber);
                DatagramSocket socket = new DatagramSocket();

                GossipData gossipResponse= new GossipData(GossipStarter.nodeNumber);
                gossipResponse.userString="p";
                gossipResponse.type="resp";
                gossipResponse.responseServer=GossipStarter.serverPort;
                gossipResponse.status=true;
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ObjectOutputStream os = new ObjectOutputStream(outputStream);
                os.writeObject(gossipResponse);
                byte[] data = outputStream.toByteArray();
                int currentPort = GossipStarter.serverPort;
                InetAddress IPAdress = InetAddress.getByName("localhost");
                DatagramPacket previousPacket = new DatagramPacket(data, data.length, IPAdress, gossipData.requestServer );
                socket.send(previousPacket);
            }else if(gossipData.userString.equals("m")){
                //System.out.println("received m");
                int min = GossipStarter.nodeData.lowValue;
                int max = GossipStarter.nodeData.highValue;
                Boolean changed = false;
                if(min>gossipData.lowValue){
                    GossipStarter.nodeData.lowValue = gossipData.lowValue;
                    GossipStarter.nodeData.minNode = gossipData.minNode;
                    changed = true;
                }
                if(max<gossipData.highValue){
                    GossipStarter.nodeData.highValue = gossipData.highValue;
                    GossipStarter.nodeData.maxNode = gossipData.maxNode;
                    changed = true;
                }
                if(changed){
                    GossipStarter.nodeData.average = (GossipStarter.nodeData.lowValue+GossipStarter.nodeData.highValue)/
                            ((GossipStarter.nodeData.maxNode-GossipStarter.nodeData.minNode)+1);
                    GossipSender gossipSender = new GossipSender();
                    gossipSender.sendUpdateNodes(GossipStarter.serverPort,GossipStarter.nodeData);
                }

            }else if(gossipData.userString.equals("d")){
                GossipSender sender = new GossipSender();
                if(GossipStarter.nodeNumber<gossipData.nodeNumber){
                    if(GossipStarter.nodeNumber== gossipData.nodeNumber-1){
                        gossipData.highValue = GossipStarter.nodeData.localValue;
                    }
                    GossipStarter.nodeData.highValue=gossipData.highValue;
                    sender.updatePreviousMax(GossipStarter.serverPort,gossipData);
                }else if(GossipStarter.nodeNumber>gossipData.nodeNumber){
                    if(GossipStarter.nodeNumber== gossipData.nodeNumber+1){
                        gossipData.lowValue = GossipStarter.nodeData.localValue;
                    }
                    GossipStarter.nodeData.lowValue=gossipData.lowValue;
                    sender.updateNextMin(GossipStarter.serverPort,gossipData);
                }
            }else if(gossipData.userString.equals("k")){
                GossipSender sender = new GossipSender();
                sender.deleteAll(GossipStarter.serverPort,gossipData);
            }else if(gossipData.userString.equals("z")){
                GossipSender sender = new GossipSender();
                if(GossipStarter.nodeNumber<gossipData.nodeNumber){
                    System.out.println("network size: "+((GossipStarter.nodeData.maxNode-GossipStarter.nodeData.minNode)+1));
                    sender.calculatePreviousSize(GossipStarter.serverPort,gossipData);
                }else if(GossipStarter.nodeNumber>gossipData.nodeNumber){
                    System.out.println("network size: "+((GossipStarter.nodeData.maxNode-GossipStarter.nodeData.minNode)+1));
                    sender.calculateNextSize(GossipStarter.serverPort,gossipData);
                }
            }else if(gossipData.userString.equals("a")){
                GossipSender sender = new GossipSender();
                int sum=0;
                for(int start = GossipStarter.nodeData.lowValue;start<=GossipStarter.nodeData.highValue;start++){
                    sum+=start;
                }
                GossipStarter.nodeData.average = sum/((GossipStarter.nodeData.highValue-GossipStarter.nodeData.lowValue)+1);
                if(GossipStarter.nodeNumber<gossipData.nodeNumber){
                    System.out.println("average: "+GossipStarter.nodeData.average);
                    sender.calculatePreviousAverage(GossipStarter.serverPort,gossipData);
                }else if(GossipStarter.nodeNumber>gossipData.nodeNumber){
                    System.out.println("average: "+GossipStarter.nodeData.average);
                    sender.calculateNextAverage(GossipStarter.serverPort,gossipData);
                }
            }else if(gossipData.userString.equals("l")){
                GossipStarter.nodeData.displayNode();
                GossipSender sender = new GossipSender();
                if(gossipData.status){
                    sender.displayPreviousLocalValue(GossipStarter.serverPort,GossipStarter.nodeData);
                }else{
                    sender.displayNextLocalValue(GossipStarter.serverPort,GossipStarter.nodeData);
                }
            }else if(gossipData.userString.equals("v")){
                GossipStarter.nodeData.lowValue= gossipData.lowValue;
                GossipStarter.nodeData.highValue=gossipData.highValue;
                int oldValue = GossipStarter.nodeData.localValue;
                GossipStarter.nodeData.localValue=gossipData.localValue;
                System.out.println("node "+GossipStarter.nodeNumber);
                System.out.println("min "+GossipStarter.nodeData.lowValue);
                System.out.println("max "+GossipStarter.nodeData.highValue);
                System.out.println("old value "+oldValue);
                System.out.println("current value "+GossipStarter.nodeData.localValue);
            }else if(gossipData.userString.equals("y")){
                System.out.println("Cycle : "+GossipStarter.nodeData.cycle);
            }
        }else if(gossipData.type.equals("resp")){
            if (gossipData.userString.equals("p")){
                System.out.println("pinged node "+gossipData.nodeNumber+" sucessfully");
            }
        }
        }catch(Exception e){
                e.printStackTrace();
            }
        //System.out.println("\nGossip Worker: In Gossip worker:" + gossipData.userString + "\n");
    }
}

class GossipData implements Serializable {

    int nodeNumber;
    int port;
    float average;
    int highValue;
    int lowValue;
    int localValue;
    int newValue;
    int minNode;
    int maxNode;
    String userString;
    int requestServer;
    int responseServer;
    String type;
    Boolean status;
    int size;
    int N;
    int cycle;


    public int calcAverage() {
        return (this.lowValue + this.highValue) / 2;
    }

    public GossipData(int nodeNumber) {
        this.nodeNumber = nodeNumber;
    }

    public String toString() {
        return "GossipData{" +
                "nodeNumber=" + nodeNumber +
                ", port=" + port +
                ", average=" + average +
                ", highValue=" + highValue +
                ", lowValue=" + lowValue +
                ", userString='" + userString + '\'' +
                ", requestServer=" + requestServer +
                ", responseServer=" + responseServer +
                ", type='" + type + '\'' +
                '}';
    }

    public void displayNode() {
        System.out.println("nodeNumber = " + nodeNumber);
        System.out.println("average = " + average);
        System.out.println("maximum value = " + highValue);
        System.out.println("minimun value = " + lowValue);
    }
}

class GossipSender{

    public void sendUpdateNodes(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString = "m";
        gossipData.type = "req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket nextPacket = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        DatagramPacket previousPacket = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(nextPacket);
        socket.send(previousPacket);

    }

    public void sendDeleteNodes(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString = "d";
        gossipData.type = "req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket nextPacket = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        DatagramPacket previousPacket = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(nextPacket);
        socket.send(previousPacket);
        System.out.println("node "+gossipData.nodeNumber+" gracefully going to sleep, can be restarted");
        System.exit(0);

    }

    public void deleteAll(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString="k";
        gossipData.type = "req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket nextPacket = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        DatagramPacket previousPacket = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(nextPacket);
        socket.send(previousPacket);
        System.out.println("wait, don't kill me!!! NOOOOOOOOOO.......");
        System.exit(0);

    }

    public void calculateNewValues(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString="v";
        gossipData.type="req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket nextPacket = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        DatagramPacket previousPacket = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(nextPacket);
        socket.send(previousPacket);

    }

    public void calculatePreviousValues(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString = "v";
        gossipData.type = "req";
        gossipData.status = true;
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket packet = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(packet);
    }

    public void calculateNextValues(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString = "v";
        gossipData.type = "req";
        gossipData.status = false;
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket packet = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        socket.send(packet);
    }

    public void displayLocalValue(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString="l";
        gossipData.type="req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket nextPacket = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        DatagramPacket previousPacket = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(nextPacket);
        socket.send(previousPacket);
    }

    public void displayPreviousLocalValue(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString="l";
        gossipData.type="req";
        gossipData.status=true;
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket previousPacket = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(previousPacket);
    }
    public void displayNextLocalValue(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString="l";
        gossipData.type="req";
        gossipData.status=false;
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket nextPacket = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        socket.send(nextPacket);
    }

    public void calculateSize(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString="z";
        gossipData.type="req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket nextPacket = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        DatagramPacket previousPacket = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(nextPacket);
        socket.send(previousPacket);

    }

    public void calculateAverage(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString="a";
        gossipData.type="req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket nextPacket = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        DatagramPacket previousPacket = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(nextPacket);
        socket.send(previousPacket);

    }

    public void updatePreviousMax(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString = "d";
        gossipData.type = "req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket packet = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(packet);

    }

    public void updateNextMin(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString = "d";
        gossipData.type = "req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket packet = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        socket.send(packet);

    }

    public void calculatePreviousSize(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString = "z";
        gossipData.type = "req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket packet = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(packet);
    }

    public void calculateNextSize(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString = "z";
        gossipData.type = "req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket packet = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        socket.send(packet);
    }

    public void calculatePreviousAverage(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString = "a";
        gossipData.type = "req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket packet = new DatagramPacket(data,data.length,IPAdress,serverPort-1);
        socket.send(packet);
    }

    public void calculateNextAverage(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        gossipData.userString = "a";
        gossipData.type = "req";
        DatagramSocket socket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");

        DatagramPacket packet = new DatagramPacket(data,data.length,IPAdress,serverPort+1);
        socket.send(packet);
    }

    public void sendPing(int serverPort, GossipData gossipData) throws Exception{
        if(GossipStarter.nodeData.N>15){
            GossipStarter.nodeData.cycle += 1;
            GossipStarter.nodeData.N = 0;
        }
        GossipStarter.nodeData.N +=1;
        DatagramSocket DGSocket = new DatagramSocket();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(gossipData);
        byte[] data = outputStream.toByteArray();
        InetAddress IPAdress = InetAddress.getByName("localhost");
        int currentPort = GossipStarter.serverPort;
        GossipSender sender = new GossipSender();
        DatagramPacket previousPacket = new DatagramPacket(data, data.length, IPAdress, currentPort-1);
        DGSocket.send(previousPacket);
        DatagramPacket nextPacket = new DatagramPacket(data, data.length, IPAdress, currentPort+1);
        DGSocket.send(nextPacket);

    }
}

class ConsoleLooper implements Runnable {

    @Override
    public void run() {
        System.out.println("CL: In the Console Looper Thread");

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println(
                "\nCL: Enter a string to send to the gossipServer, (or, quit/ stopserver): ");

        try {
            String consoleInput;
            do {
                System.out.println(
                        "\n console is still active...");
                // TODO("need to learn what it does")
                System.out.flush();
                consoleInput = in.readLine();

                if (consoleInput.indexOf("quit") > -1) {
                    System.out.println("CL: Exiting now by user request.\n");
                    System.exit(0);
                } else if (consoleInput.equals("t")) {
                    System.out.println("t, Lists all of the implemented commands");
                    System.out.println("l, Displays the Local Values at this node");
                    System.out.println(
                            "p, Pings the nodes above and below and displays if they're reachable along with node numbers");
                    System.out.println(
                            "m, Display the minimum value and maximum value currently in the network, along with the Node ID associated with each of them");
                    System.out.println(
                            "a, Calculate the average of all the local values in the [sub-]network. Display on ALL NODES, preceded by Local Node ID and Local Node Value");
                    System.out.println("z, Calculate the current [sub-]network size");
                    System.out.println(
                            "v, Create new random values throughout the network at each node. Display the old value and the new value on each node");
                    System.out.println("d, Deletes the current node and gracefully closes the socket");
                    System.out.println("k, Kills the entire network");
                    System.out.println(
                            "y, Display the number of cycles since the beginning of time on every node in the network");
                    System.out.println(
                            "N, N is an integer. Set the number of gossip messages for the entire [sub-]network that can be sent to the same neighbor during any one cycle."
                                    +
                                    " For example: \"15\" would limit the messages to 15. Note that it is possible that when sub-networks have been joined, nodes will have different values of N");

                } else if (consoleInput.equals("l")) {
                    GossipStarter.nodeData.displayNode();
                    GossipSender sender = new GossipSender();
                    sender.displayPreviousLocalValue(GossipStarter.serverPort,GossipStarter.nodeData);
                    sender.displayNextLocalValue(GossipStarter.serverPort,GossipStarter.nodeData);
                } else if (consoleInput.equals("p")) {
                    GossipSender sender = new GossipSender();
                    GossipData gossipReq = new GossipData(GossipStarter.nodeNumber);
                    gossipReq.requestServer=GossipStarter.serverPort;
                    gossipReq.type="req";
                    gossipReq.userString="p";
                    gossipReq.requestServer=GossipStarter.serverPort;
                    sender.sendPing(GossipStarter.serverPort,gossipReq);

                }else if(consoleInput.equals("m")){
                    GossipSender gossipSender = new GossipSender();
                    gossipSender.sendUpdateNodes(GossipStarter.serverPort,GossipStarter.nodeData);
                    System.out.println("node "+GossipStarter.nodeNumber);
                    System.out.println("min "+GossipStarter.nodeData.lowValue);
                    System.out.println("max "+GossipStarter.nodeData.highValue);

                }else if(consoleInput.equals("d")){
                    GossipSender gossipSender = new GossipSender();
                    GossipData deleteData = new GossipData(GossipStarter.nodeNumber);
                    deleteData.type="req";
                    deleteData.userString="r";
                    gossipSender.sendDeleteNodes(GossipStarter.serverPort,deleteData);
                    System.out.println("wait, don't kill me!!!!!! NOOOOOOOOOOOOOO...");
                    System.exit(0);
                }else if(consoleInput.equals("k")){
                    GossipSender gossipSender = new GossipSender();
                    GossipData deleteData = new GossipData(GossipStarter.nodeNumber);
                    deleteData.type="req";
                    deleteData.userString="k";
                    gossipSender.deleteAll(GossipStarter.serverPort,deleteData);
                    System.out.println("wait, don't kill me!!!!!! NOOOOOOOOOOOOOO...");
                    System.exit(0);
                }else if(consoleInput.equals("z")){
                    System.out.println("network size: "+((GossipStarter.nodeData.maxNode-GossipStarter.nodeData.minNode)+1));
                    GossipSender sender = new GossipSender();
                    GossipData size = new GossipData(GossipStarter.nodeNumber);
                    sender.calculateSize(GossipStarter.serverPort,size);
                }else if(consoleInput.equals("a")){
                    GossipSender sender = new GossipSender();
                    int sum=0;
                    for(int start = GossipStarter.nodeData.lowValue;start<=GossipStarter.nodeData.highValue;start++){
                        sum+=start;
                    }
                    GossipStarter.nodeData.average = sum/((GossipStarter.nodeData.highValue-GossipStarter.nodeData.lowValue)+1);
                    System.out.println("average: "+GossipStarter.nodeData.average);
                    sender.calculateAverage(GossipStarter.serverPort, GossipStarter.nodeData);
                }else if(consoleInput.equals("v")){

                    //calculating new value
                    List<Integer> newValues = new ArrayList<>();
                    Random random = new Random();
                    int start = GossipStarter.nodeData.minNode;
                    int end = GossipStarter.nodeData.maxNode;
                    for (int i=0 ;i<(end-start)+1;i++){
                        newValues.add(random.nextInt(98)+1);
                    }
                    newValues.sort(Comparator.naturalOrder());
                    int minValue = newValues.get(0);
                    int maxValue = newValues.get(newValues.size()-1);

                    for( int i = start;i<end;i++){
                        GossipData data = new GossipData(i);
                        data.lowValue = minValue;
                        data.highValue = maxValue;
                        data.localValue = newValues.get(i);
                        data.type="req";
                        data.userString="v";
                        data.port=48100+i;
                        DatagramSocket socket = new DatagramSocket();
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        ObjectOutputStream os = new ObjectOutputStream(outputStream);
                        os.writeObject(data);
                        byte[] data1 = outputStream.toByteArray();
                        InetAddress IPAdress = InetAddress.getByName("localhost");

                        DatagramPacket nextPacket = new DatagramPacket(data1,data1.length,IPAdress,data.port);
                        socket.send(nextPacket);
                    }

                }else if(consoleInput.equals("y")){
                    int start = GossipStarter.nodeData.minNode;
                    int end = GossipStarter.nodeData.maxNode;
                    for( int i = start;i<end;i++){
                        GossipData data = new GossipData(i);
                        data.type="req";
                        data.userString="y";
                        data.port=48100+i;
                        DatagramSocket socket = new DatagramSocket();
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        ObjectOutputStream os = new ObjectOutputStream(outputStream);
                        os.writeObject(data);
                        byte[] data1 = outputStream.toByteArray();
                        InetAddress IPAdress = InetAddress.getByName("localhost");

                        DatagramPacket nextPacket = new DatagramPacket(data1,data1.length,IPAdress,data.port);
                        socket.send(nextPacket);
                }

            }
            } while (true);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
