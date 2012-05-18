package roge.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import roge.net.ConnectionServer.ClientConnectListener;

/**
 * @author Nicholas Rogé
 */
public class ConnectionClient{
    /**
     * Interface which any classes that would like to receive data updates from this object 
     * 
     * @author Nicholas Rogé
     */
    public static interface DataReceivedListener{
        public void onDataReceived(ConnectionClient client,String data);
   }
    
    private String                     __host_address;
    private DataInputStream            __input;
    private List<DataReceivedListener> __listeners;
    private Thread                     __message_listener;
    private DataOutputStream           __output;
    private int                        __port;
    private boolean                    __server_ready;
    private Socket                     __socket;
    
    /*Begin Constructors*/
    /**
     * 
     * 
     * @param socket Socket to use as the base for this Class
     * 
     * @throws IllegalArgumentException Thrown if the socket parameter is null.
     */
    public ConnectionClient(Socket socket,boolean require_server_sync){
        if(socket==null){
            throw new IllegalArgumentException();
        }
        
        
        this.__socket=socket;
        
        this.__host_address=this.__socket.getInetAddress().getHostAddress();
        this.__port=this.__socket.getPort();
        
        this.__server_ready=!require_server_sync;
        
        this._connect();
    }
    
    public ConnectionClient(String host_address,int port,boolean require_server_sync){
        this.__host_address=host_address;
        this.__port=port;
        
        this.__server_ready=!require_server_sync;
    }
    /*End Constructors*/
    
    /*Begin Getter Methods*/
    public String getIP(){
        return this.__host_address;
    }
    /*End Getter Methods*/
    
    /*Begin Other Essential Methods*/
    public void addDataRecievedListener(DataReceivedListener listener){
        if(this.__listeners==null){
            this.__listeners=new ArrayList<DataReceivedListener>();
        }

        this.__listeners.add(listener);
    }
    
    public void broadcastData(String data){
        if(this.__listeners!=null){
            for(DataReceivedListener listener:this.__listeners){
                listener.onDataReceived(this,data);
            }
        }
    }
    
    public void connect() throws UnknownHostException,IOException{
        if(this.__socket==null){
            this.__socket=new Socket(this.__host_address,this.__port);
        }
        
        this._connect();
    }
    
    protected void _connect(){
        try{
            this.__input=new DataInputStream(this.__socket.getInputStream());
            this.__output=new DataOutputStream(this.__socket.getOutputStream());            
        }catch(IOException e){
            System.out.print("Could not open the read or write stream on this client.  Cause:  "+e.getMessage());
        }
        
        this._startMessageListener();
        
        while(!this.__server_ready){  //Block while the server isn't ready to receive data.
            try {
                Thread.sleep(10);  //We have to add a small break to allow the variable to actually be modified.
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
    public void disconnect(){
        if(this.__socket!=null){
            try{
                this.__message_listener.interrupt();
                this.__input.close();
                this.__output.close();
                this.__socket.close();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    
    public void send(String data) throws IOException{
        if(this.__socket==null){
            throw new IOException("You must initialize the connection using the \"connect()\" method before you can send anything.");
        }
        
        this.__output.writeUTF(data);
    }
    
    protected void _startMessageListener(){
        this.__message_listener=new Thread(){
            @Override public void run(){
                String data=null;

                try{
                    while(true){
                        data=ConnectionClient.this.__input.readUTF();
                        System.out.print("Data message recieved:  "+data+"\n");
                        if(data==null){
                            break;
                        }else{
                            if(ConnectionClient.this.__server_ready){
                                ConnectionClient.this.broadcastData(data);
                            }else{
                                if(data.equals(ConnectionServer.CONNECT_SUCCESS)){
                                    ConnectionClient.this.__server_ready=true;
                                }
                            }
                        }
                    }
                }catch(EOFException e){
                    System.out.print("Cannot read any further:  Remote connection closed.\n");
                }catch(IOException e){
                    System.out.print("IOException caught while listening for incoming data!  Message:  "+e.getMessage());
                }
            }
        };
        this.__message_listener.start();
    }
    /*End Other Essential Methods*/
}
