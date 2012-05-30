package roge.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import roge.net.ConnectionServer.Signals;

/*
 * TODO_HIGH:  As it stands, there is an issue with the current system of ensured message arrival.  Let's use this scenario:  Message A and Message B are both sent at roughly the same time, however, something happens to Message A and it never arrives at its destination.  This client will recieve the Ack for Message B and allow the thread attempting to send Message A to continue running, however, Message B's sender's thread will continue to block until a new Ack is received.
 */

/**
 * This object should be used to connect to a server which has an actively running <code>ConnectionServer</code> object, and can be used to transfer data back and forth through the connection.
 * 
 * @author Nicholas Rogé
 */
public class ConnectionClient{  
    /**List of signals this object may send.*/
    public static class Signals{
        /**Signal which is sent when a piece of data is received by the server, indicating that it was successfully received.*/
        public static class Ack extends Signal{
            private static final long serialVersionUID = -4158558968368644951L;
            
            private Class<?> __received_signal_class;
            
            
            /**
             * Constructs the object.
             * 
             * @param object This should be the object that was received by the server.
             */
            public Ack(Object object){
                this.__received_signal_class=object.getClass();
            }
            
            /**
             * Gets the Class of the data that was received by the server.  
             * 
             * @return The Class of the data that was received by the server.
             */
            public Class<?> getReceivedSignalClass(){
                return this.__received_signal_class;
            }
        }
    }
    
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
     * Interface which allows the broadcasting of an object whenever one is sent by an this class.
     * 
     * @author Nicholas Rogé
     */
    public static interface DataSendListener{
        /**
         * Called whenever a ConnectionClient attempts to send any anything.
         * 
         * @param client Client who sent the signal.
         * @param data Data sent.
         * 
         * @return If <code>true</code> is returned, then the object will be allowed to be sent.  Otherwise, the object will not be sent.
         */
        public boolean onDataSend(ConnectionClient client,Object data);
    }
    
    /**
     * Interface which allows the broadcasting of a signal to those that wish to receive it.
     * 
     * @author Nicholas Rogé
     */
    public static interface SignalReceivedListener{
        /**
         * Whenever a ConnectionSignal is received on the ConnectionClient object, the object calls this method.
         * 
         * @param client Client who received the signal.
         * @param signal Signal received.
         */
        public void onSignalReceived(ConnectionClient client,Signal signal);
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
         * @param client Client who received the message.
         * @param data Object which was received.
         */
        public void onDataReceived(ConnectionClient client,Object data);
    }
    
    public static final int ENSURED_DATA_RECEIPT_TIMEOUT_PERIOD=3000;  //3 seconds
    public static final int MAXIMUM_SEND_RETRY_COUNT=3;
    
    private Signals.Ack                            __ack_received;
    private String                         __host_address;
    private ObjectInputStream              __input;
    private List<DataReceivedListener>     __data_received_listeners;
    private List<DataSendListener>         __data_send_listeners;
    private Thread                         __message_listener;
    private ObjectOutputStream             __output;
    private int                            __port;
    private int                            __server_status;
    private List<SignalReceivedListener>   __signal_received_listeners;
    private Socket                         __socket;
    private boolean                        __verbose;

    
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
        
        this.__server_status=1;
        
        this.setVerbose(false);
        
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
        
        if(require_server_sync){
            this.__server_status=0;
        }else{
            this.__server_status=1;
        }
        
        this.setVerbose(false);
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
    
    /**
     * Returns the status of the connection to the server.
     * 
     * @return Will return <code>true</code> if the connection has been made, or <code>false</code> otherwise.
     */
    public boolean isConnected(){
        if(this.__socket==null){
            return false;
        }else{
            return !this.__socket.isClosed();
        }
    }
    /*End Getter Methods*/
    
    /*Begin Setter Methods*/
    /**
     * Allows or disallows the output of debug text.
     * 
     * @param verbose If <code>true</code> debugging text will be allowed.
     */
    public void setVerbose(boolean verbose){
        this.__verbose=verbose;
    }
    /*End Setter Methods*/
    
    /*Begin Other Essential Methods*/
    /**
     * Adds a listener to be called when a signal is received.
     * 
     * @param listener Object to be called upon signal receipt.
     */
    public void addSignalReceivedListener(SignalReceivedListener listener){
        if(this.__signal_received_listeners==null){
            this.__signal_received_listeners=new ArrayList<SignalReceivedListener>();
        }
        
        this.__signal_received_listeners.add(listener);
    }
    
    /**
     * Adds a listener to be called when data is received.
     * 
     * @param listener Object to be called upon data receipt.
     */
    public void addDataRecievedListener(DataReceivedListener listener){
        if(this.__data_received_listeners==null){
            this.__data_received_listeners=new ArrayList<DataReceivedListener>();
        }

        this.__data_received_listeners.add(listener);
    }
    
    /**
     * Adds a listener to be called when data is attempting to be sent.
     * 
     * @param listener Object to be called upon data send attempt.
     */
    public void addDataSendListener(DataSendListener listener){
        if(this.__data_send_listeners==null){
            this.__data_send_listeners=new ArrayList<DataSendListener>();
        }
        
        this.__data_send_listeners.add(listener);
    }
    
    /**
     * Calls the onDataReceived method of all its listeners.
     * 
     * @param data Object being broadcasted.  If this parameter is null, a <code>NullPointerException</code> will be thrown.
     */
    protected void _broadcastDataReceived(Object data){
        if(data==null){
            throw new NullPointerException();
        }
        
        if(this.__data_received_listeners!=null){
            for(DataReceivedListener listener:this.__data_received_listeners){
                listener.onDataReceived(this,data);
            }
        }
    }
    
    /**
     * Calls the onDataSend method of all its listeners.
     * 
     * @param data Data attempting to be sent.
     * 
     * @return Returns <code>true</code> if the data should be sent, and <code>false</code> otherwise.
     */
    protected boolean _broadcastDataSend(Object data){
        boolean send_data=true;
        
        
        if(data==null){
            throw new NullPointerException();
        }
        
        if(this.__data_send_listeners!=null){
            for(DataSendListener listener:this.__data_send_listeners){
                if(!listener.onDataSend(this,data)){
                    if(send_data){  //This is to ensure that all the listeners get called.
                        send_data=false;
                    }
                }
            }
        }
        
        return send_data;
    }
    
    /**
     * Calls the onSignalReceived method of all its listeners.
     * 
     * @param signal Signal being broadcasted.  If this parameter is null, a <code>NullPointerException</code> will be thrown.
     */
    protected void _broadcastSignalReceived(Signal signal){
        if(signal==null){
            throw new NullPointerException();
        }
        
        this._onSignalReceived(this,signal);
        
        if(this.__signal_received_listeners!=null){
            for(SignalReceivedListener listener:this.__signal_received_listeners){
                listener.onSignalReceived(this,signal);
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
        }catch(IOException e){
            if(this.__verbose){
                System.out.print("Could not open the write stream on this client.  Cause:\n    "+e.getMessage()+"\n");
            }
            
            throw e;
        }
        
        new Thread(new Runnable(){
            @Override public void run(){
                try{
                    ConnectionClient.this.__input=new ObjectInputStream(ConnectionClient.this.__socket.getInputStream());
                }catch(IOException e){
                    System.out.print("Could not open the write stream on this client.  Cause:\n    "+e.getMessage()+"\n");
                    if(ConnectionClient.this.__verbose){
                        e.printStackTrace();
                    }
                }
            }
        }).start();

        this._startMessageListener();
        
        while(this.__server_status==0){  //Block while the server isn't ready to receive data.
            try{
                Thread.sleep(10);  //We have to add a small break to allow the variable to actually be modified.  //Ten milliseconds is the minimum amount of required to wait for this loop to function properly
            }catch(InterruptedException e){
                if(this.__verbose){
                    e.printStackTrace();
                }
            }
        }
        
        if(this.__server_status==-1){
                this.disconnect();
                
                throw new IOException("Connection to server refused.");
        }
    }
    
    /**
     * Disconnects this object from the server.  This method should ALWAYS be called when the connection will no longer be used.
     */
    public void disconnect(){
        this.disconnect(true);
    }
    
    /**
     * Disconnects this object from the server.  This method should ALWAYS be called when the connection will no longer be used.
     * 
     * @param send_connection_close_notice Unless you are acting as the server, this should always be true.  In most cases, it is sufficient to simply use the {@link ConnectionClient#disconnect()} method.
     */
    public void disconnect(boolean send_connection_close_notice){
        if(this.__socket!=null){
            try{
                if(send_connection_close_notice){
                    this.send(new ConnectionServer.Signals.CloseConnection(),true);
                }

                this.__message_listener.interrupt();
                this.__input.close();
                this.__output.close();
                this.__socket.close();
            }catch(Exception e){
                if(this.__verbose){
                    e.printStackTrace();
                }
            }
        }
    }
    
    protected void _onSignalReceived(ConnectionClient client,Signal signal){
        if(signal instanceof Signals.Ack){
            this.__ack_received=(Signals.Ack)signal;
        }else if(signal instanceof ConnectionServer.ConnectSuccessSignal){
            this.__server_status=1; //1 indicates that the server is ready to recieve data.
        }else if(signal instanceof ConnectionServer.ConnectFailureSignal){
            this.__server_status=-1;
        }else if(signal instanceof ConnectionServer.Signals.CloseConnection){
            this.disconnect(false);  //We don't need to send a reply to the server
        }
    }
    
    /**
     * Removes the given DataReceivedListener from the list of listeners.
     * 
     * @param listener Listener to be removed.
     */
    public void removeDataReceivedListener(DataReceivedListener listener){
        if(this.__data_received_listeners==null){
            return;
        }else if(!this.__data_received_listeners.contains(listener)){
            return;
        }
        
        this.__data_received_listeners.remove(listener);
    }
    
    /**
     * Removes the given DataSendListener from the list of listeners.
     * 
     * @param listener Listener to be removed.
     */
    public void removeDataSendListener(DataSendListener listener){
        if(this.__data_send_listeners==null){
            return;
        }else if(!this.__data_send_listeners.contains(listener)){
            return;
        }
        
        this.__data_send_listeners.remove(listener);
    }
    
    /**
     * Removes the given SignalReceivedListener from the list of listeners.
     * 
     * @param listener Listener to be removed.
     */
    public void removeSignalReceivedListener(SignalReceivedListener listener){
        if(this.__signal_received_listeners==null){
            return;
        }else if(!this.__signal_received_listeners.contains(listener)){
            return;
        }
        
        this.__signal_received_listeners.remove(listener);
    }
    
    /**
     * Wrapper for the <code>send(Object,boolean)</code> method.  Sets the ensure_arrival parameter to false.
     * 
     * @param data Data to be sent to the server.  If this is null, a NullPointerException will be thrown.
     * 
     * @return Will return false if the data couldn't be sent, or true if the data was sent successfully.
     */
    public boolean send(Object data) throws IOException,NullPointerException{
        return this.send(data,false);
    }
    
    /**
     * Sends the given data to the server.  IMPORTANT_NOTE:  This method will block the current thread until an Ack is successfully received.
     * 
     * @param data Data to be sent to the server.  If this is null, a NullPointerException will be thrown.
     * @param ensure_arrival If this parameter is true, this method will attempt to make sure that the data being sent arrives.
     * 
     * @return Will return false if the data couldn't be sent, or true if the data was sent successfully.
     * 
     * @throws IOException Thrown if the socket has not yet been initialized (with cause of type <code>ConnectionClient.NotConnected</code>, or if an issue occurred when attempting to send the data.
     */
    public boolean send(Object data,boolean ensure_arrival) throws IOException{
        int  retry_count=1;
        long wait_begin_time=0;
        
        
        if(data==null){
            throw new NullPointerException();
        }else if(this.__socket==null){
            throw new IOException(new NotConnected());
        }
        
        if(!this._broadcastDataSend(data)){
            return false;
        }
        
        
        if(ensure_arrival){
            this.__ack_received=null;;
            
            while(true){
                this.__output.writeObject(data);
                
                wait_begin_time=System.currentTimeMillis();
                while(true){
                    if(this.__ack_received!=null){
                        //TODO:  figure out a way to determine if the ACK received is the correct ACK for the data that was sent.
                        
                        break;
                    }
                    
                    if((System.currentTimeMillis()-wait_begin_time)>ConnectionClient.ENSURED_DATA_RECEIPT_TIMEOUT_PERIOD){
                        retry_count++;
                        
                        if(retry_count>ConnectionClient.MAXIMUM_SEND_RETRY_COUNT){
                            return false;
                        }
                        
                        break;
                    }
                    
                    try{
                        Thread.sleep(10);
                    }catch(InterruptedException e){
                        e.printStackTrace();
                    }
                }
                
                break;
            }
        }else{
            this.__output.writeObject(data);
        }
        
        return true;
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
        
        while(this.__input==null){  //Block while the input stream is null.  (I.E.:  While it's waiting for the headers from the other side of the socket.
            try{
                Thread.sleep(10);
            }catch(InterruptedException e){
                if(this.__verbose){
                    e.printStackTrace();
                }
            }
        }
        
        this.__message_listener=new Thread(new Runnable(){
            @Override public void run(){
                Object data=null;

                while(true){
                    try{
                            data=ConnectionClient.this.__input.readObject();
                            if(ConnectionClient.this.__verbose){
                                System.out.print("Data recieved:  "+data.toString()+"\n");
                            }
    
                            if(data instanceof Signal){
                                ConnectionClient.this._broadcastSignalReceived((Signal)data);
                            }else{
                                ConnectionClient.this._broadcastDataReceived(data);
                            }
                            
                            ConnectionClient.this.send(new Signals.Ack(data),false);
                    }catch(ClassNotFoundException e){
                        if(ConnectionClient.this.__verbose){
                            System.out.print("Invalid object recieved.  Discarding.\n");
                        }
                    }catch(EOFException e){
                        if(ConnectionClient.this.__verbose){
                            System.out.print("Cannot read any further:  Remote connection closed.\n");
                        }
                        
                        break;
                    }catch(IOException e){
                        if(e.getMessage().equals("socket closed")){
                            break;
                        }else{
                            System.out.print("IOException caught while listening for incoming data!  Message:\n    "+e.getMessage()+"\n");
                            if(ConnectionClient.this.__verbose){
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        });
        this.__message_listener.start();
    }
    /*End Other Essential Methods*/
}
