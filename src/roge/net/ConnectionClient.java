package roge.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * This object should be used to connect to a server which has an actively running <code>ConnectionServer</code> object, and can be used to transfer data back and forth through the connection.
 * 
 * @author Nicholas Rogé
 */
public class ConnectionClient{    
    /**
     * Describes a condition in which the user has attempted to call the <code>send</code> method of this object without first having initialized the object by calling the <code>connect</code> method.
     * 
     * @author Nicholas Rogé
     */
    public static class NotConnected extends Throwable{
        private static final long serialVersionUID = 3442734742955673377L;

        @Override public String toString(){
            return "You must initialize the connection using the \"connect()\" method before you can send anything.";
        }
    }
    
    /**
     * Interface which any classes that would like to receive data updates from this object should implement.
     * 
     * @author Nicholas Rogé
     */
    public static interface DataReceivedListener{
        /**
         * Whenever data on the ConnectionClient object, the object calls this method.
         * 
         * @param client Client which received the message.
         * @param data Object which was received.
         */
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
     * Constructs the object, using an already initialized socket to connect with.
     * 
     * @param socket Socket to use as the base for this object
     * 
     * @throws IOException Throws IOException in the event that it can't open the read or write streams on this socket.
     */
    public ConnectionClient(Socket socket) throws IOException{
        if(socket==null){
            throw new IllegalArgumentException();
        }
        
        
        this.__socket=socket;
        
        this.__host_address=this.__socket.getInetAddress().getHostAddress();
        this.__port=this.__socket.getPort();
        
        this.__server_ready=false;
        
        this._connect();
    }
    
    /**
     * Constructs the object, connected to the given host and port.
     * 
     * @param host_address Server with which connect.  This can be in the form of a URL or IP address.
     * @param port Port on the server though which the servers application is running.
     * @param require_server_sync This should almost always be true.  Passing true as the argument ensures that the server is ready to receive data before anything is sent.
     */
    public ConnectionClient(String host_address,int port,boolean require_server_sync){
        this.__host_address=host_address;
        this.__port=port;
        
        this.__server_ready=!require_server_sync;
    }
    /*End Constructors*/
    
    /*Begin Getter Methods*/
    /**
     * Returns the IP with which this socket is attempting to connect.
     * 
     * @return The IP with which this socket is attempting to connect.
     */
    public String getIP(){
        return this.__host_address;
    }
    /*End Getter Methods*/
    
    /*Begin Other Essential Methods*/
    /**
     * Adds a listener to be called when data is received.
     * 
     * @param listener Object to be called upon data receipt.
     */
    public void addDataRecievedListener(DataReceivedListener listener){
        if(this.__listeners==null){
            this.__listeners=new ArrayList<DataReceivedListener>();
        }

        this.__listeners.add(listener);
    }
    
    /**
     * Calls the onDataReceived method of all its listeners.
     * 
     * @param data Object being broadcasted.  If this parameter is null, a <code>NullPointerException</code> will be thrown.
     */
    protected void _broadcastData(Object data){
        if(data==null){
            throw new NullPointerException();
        }
        
        if(this.__listeners!=null){
            for(DataReceivedListener listener:this.__listeners){
                listener.onDataReceived(this,data);
            }
        }
    }
    
    /**
     * Initializes the connection.
     * 
     * @throws UnknownHostException Thrown if the host given upon construction cannot be found.
     * @throws IOException Thrown if the input or output streams on the object couldn't be opened.
     */
    public void connect() throws UnknownHostException,IOException{
        if(this.__socket==null){
            this.__socket=new Socket(this.__host_address,this.__port);
            
            this._connect();
        }
    }
    
    /**
     * Initializes the input and output streams.
     * 
     * @throws IOException Thrown if the input or output streams on the object couldn't be opened.
     */
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
    
    /**
     * Disconnects this object from the server.  This method should ALWAYS be called before a program is closed.
     */
    public void disconnect(){
        if(this.__socket!=null){
            try{
                this.send(ConnectionServer.CLOSE_CONNECTION);
                
                this.__message_listener.interrupt();
                this.__input.close();
                this.__output.close();
                this.__socket.close();
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Sends the given data to the server.
     * 
     * @param data Data to be sent to the server.  If this is null, a NullPointerException will be thrown.
     * 
     * @throws IOException Thrown if the socket has not yet been initialized (with cause of type <code>ConnectionClient.NotConnected</code>, or if an issue occurred when attempting to send the data.
     */
    public void send(Object data) throws IOException{
        if(data==null){
            throw new NullPointerException();
        }else if(this.__socket==null){
            throw new IOException(new NotConnected());
        }
        
        this.__output.writeObject(data);
    }
    
    /**
     * Starts the message listener loop.
     */
    protected void _startMessageListener(){
        if(this.__message_listener!=null){
            if(this.__message_listener.isAlive()){
                return;  //Don't want to accidentally start another thread.
            }
        }
        
        this.__message_listener=new Thread(){
            @Override public void run(){
                Object data=null;

                try{
                    while(true){
                        data=ConnectionClient.this.__input.readObject();
                        System.out.print("Data recieved:  "+data.toString()+"\n");

                        if(ConnectionClient.this.__server_ready){
                            ConnectionClient.this._broadcastData(data);
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
