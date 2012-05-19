package roge.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
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
     * @author Nicholas Rogé
     */
    public static class NotConnected extends Throwable{
        private static final long serialVersionUID = 3442734742955673377L;

        @Override public String toString(){
            return "You must initialize the connection using the \"connect()\" method before you can send anything.";
        }
    }
    
    /**
     * Interface which any classes that would like to receive data updates from this object 
     * 
     * @author Nicholas Rogé
     */
    public static interface DataReceivedListener{
        public void onDataReceived(ConnectionClient client,Object data);
   }
    
    private String                     __host_address;
    private ObjectInputStream          __input;
    private List<DataReceivedListener> __listeners;
    private Thread                     __message_listener;
    private ObjectOutputStream         __output;
    private int                        __port;
    private boolean                    __server_ready;
    private Socket                     __socket;
    
    /*Begin Constructors*/
    /**
     * 
     * 
     * @param socket Socket to use as the base for this Class
     * 
     * @throws IOException Throws IOException in the event that it can't open the read or write streams on this socket.
     */
    public ConnectionClient(Socket socket,boolean require_server_sync) throws IOException{
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
    
    public void broadcastData(Object data){
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
    
    protected void _connect() throws IOException{
        try{
            this.__output=new ObjectOutputStream(this.__socket.getOutputStream());
            this.__output.flush();
            
            this.__input=new ObjectInputStream(this.__socket.getInputStream());            
        }catch(IOException e){
            System.out.print("Could not open the read or write stream on this client.  Cause:\n    "+e.getMessage()+"\n");
            
            throw e;
        }

        this._startMessageListener();
        
        while(!this.__server_ready){  //Block while the server isn't ready to receive data.
            try {
                Thread.sleep(10);  //We have to add a small break to allow the variable to actually be modified.  //Ten milliseconds is the minimum amount of required to wait for this loop to function properly
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
    
    public void send(Object data) throws IOException{
        if(this.__socket==null){
            throw new IOException(new NotConnected());
        }
        
        this.__output.writeObject(data);
    }
    
    protected void _startMessageListener(){
        this.__message_listener=new Thread(){
            @Override public void run(){
                Object data=null;

                try{
                    while(true){
                        data=ConnectionClient.this.__input.readObject();
                        System.out.print("Data recieved:  "+data.toString()+"\n");

                        if(ConnectionClient.this.__server_ready){
                            ConnectionClient.this.broadcastData(data);
                        }else{
                            if(data.equals(ConnectionServer.CONNECT_SUCCESS)){
                                ConnectionClient.this.__server_ready=true;
                            }else{
                                ConnectionClient.this.disconnect();
                                
                                throw new IOException("Could not connect to the server.  Connection closed.  Use the \"connect()\" method to attempt a reconnect.");
                            }
                        }
                    }
                }catch(ClassNotFoundException e){
                    System.out.print(";;;\n");
                }catch(EOFException e){
                    System.out.print("Cannot read any further:  Remote connection closed.\n");
                }catch(IOException e){
                    System.out.print("IOException caught while listening for incoming data!  Message:\n    "+e.getMessage()+"\n");
                }
            }
        };
        this.__message_listener.start();
    }
    /*End Other Essential Methods*/
}
