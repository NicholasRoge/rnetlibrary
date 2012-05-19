package roge.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import roge.net.ConnectionClient.DataReceivedListener;

/**
 * @author Nicholas Rogé
 */
public class ConnectionServer implements DataReceivedListener{
    /**Data that should be sent when a client is closing its connection to the server.*/
    public static final String CLOSE_CONNECTION="connection_close";
    /**Data that will be received by the client if its connection to the server was a success.*/
    public static final String CONNECT_SUCCESS="connect_success";
    /**Data that will be received by the client if its connection to the server was a failure.*/
    public static final String CONNECT_FAILURE="connect_failure";
    
    /**
     * Interface which classes that would like notification of a new client connection should implement. 
     * 
     * @author Nicholas Rogé
     */
    public static interface ClientConnectListener{
        /**
         * Called when a client attempts to connect to the server.
         * 
         * @param client Client that is requesting access to the server.
         * 
         * @return Should return <code>true</code> if the client should be accepted, and <code>false</code> otherwise.
         */
        public boolean onClientConnect(ConnectionClient client);
    }
    
    /**
     * Interface which classes that would like notification of a client disconnecting should implement. 
     * 
     * @author Nicholas Rogé
     */
    public static interface ClientDisconnectListener{
        /**
         * Called when a client disconnects from the server.
         * 
         * @param client Client which is disconnecting itself.
         */
        public void onClientDisconnect(ConnectionClient client);
    }
    
    private List<ConnectionClient>         __clients;
    private List<ClientConnectListener>    __client_connect_listeners;
    private List<ClientDisconnectListener> __client_disconnect_listeners;
    private int                            __port;
    private ServerSocket                   __socket;
    private Thread                         __new_connection_listener;
    
    
    /*Begin Constructors*/
    /**
     * Constructs the object, telling it to listen to the given port.
     * 
     * @param port Port this object should listen to.
     */
    public ConnectionServer(int port){
        this.__port=port;
    }
    /*End Constructors*/
    
    /*Begin Overridden Methods*/
    @Override public void onDataReceived(ConnectionClient client,Object data){
        if(((String)data).equals(ConnectionServer.CLOSE_CONNECTION)){
            for(ClientDisconnectListener listener:this.getClientDisconnectListeners()){
                listener.onClientDisconnect(client);
            }
            
            client.disconnect();
            
            this.getClientList().remove(client);
        }
    }
    /*End Overridden Methods*/
    
    /*Begin Getter Methods*/
    /**
     * Returns the list of all the clients which are connected to this object.
     * 
     * @return The list of all the clients which are connected to this object.
     */
    public List<ConnectionClient> getClientList(){
        if(this.__clients==null){
            this.__clients=new ArrayList<ConnectionClient>();
        }
        
        return this.__clients;
    }
    
    /**
     * Returns the list of all Objects which are listening for incoming client connections.
     * 
     * @return The list of all Objects which are listening for incoming client connections.
     */
    public List<ClientConnectListener> getClientConnectListeners(){
        if(this.__client_connect_listeners==null){
            this.__client_connect_listeners=new ArrayList<ClientConnectListener>();
        }
        
        return this.__client_connect_listeners;
    }
    
    /**
     * Returns the list of all Objects which are listening for any clients that are disconnecting.
     * 
     * @return The list of all Objects which are listening for any clients that are disconnecting.
     */
    public List<ClientDisconnectListener> getClientDisconnectListeners(){
        if(this.__client_disconnect_listeners==null){
            this.__client_disconnect_listeners=new ArrayList<ClientDisconnectListener>();
        }
        
        return this.__client_disconnect_listeners;
    }
    /*End Getter Methods*/
    
    /*Begin Other Essential Methods*/
    /**
     * Accepts or declines the client from connecting to the server.
     * 
     * @param client Client attempting to connect to the server.
     */
    protected void _acceptClient(ConnectionClient client){
        final SimpleDateFormat formatter=new SimpleDateFormat("HH:mm:ss' on 'dd/MM/yyyy");

        boolean accept_client=true;
        

        for(ClientConnectListener listener:this.getClientConnectListeners()){
            if(!listener.onClientConnect(client)){
                accept_client=false;
                
                break;
            }
        }
        
        if(accept_client){
            this.getClientList().add(client);
            try{
                client.send(ConnectionServer.CONNECT_SUCCESS);
            }catch(IOException e){
                e.toString();
            }

            client.addDataRecievedListener(this);
            System.out.print("Client connected from "+client.getIP()+" at "+formatter.format(new Date())+"\n\n");
        }else{
            try{
                client.send(ConnectionServer.CONNECT_FAILURE);
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Adds a listener to be called when a client attempts to connect.
     * 
     * @param listener Listener to be called when a client attempts to connect.
     */
    public void addClientConnectListener(ClientConnectListener listener){
        if(!this.getClientConnectListeners().contains(listener)){
            this.getClientConnectListeners().add(listener);
        }
    }
    
    /**
     * Adds a listener to be called when a client disconnects from the server.
     * 
     * @param listener Listener to be called when a client disconnects from the server.
     */
    public void addClientDisconnectListener(ClientDisconnectListener listener){
        if(!this.getClientDisconnectListeners().contains(listener)){
            this.getClientDisconnectListeners().add(listener);
        }
    }
    
    /**
     * Starts the server.
     */
    public void start(){
        try{
            System.out.print("Starting server on port "+this.__port+"...\n");
            this.__socket=new ServerSocket(this.__port);
        }catch(IOException e){
            System.out.print("Could not start the server succesfully...\nExiting.");
            
            System.exit(1);
        }finally{
            System.out.print("Server started successfully.\n\n");
        }
        
        this._startClientListener();
    }
    
    /**
     * Starts the thread which listens for new clients attempting to connect to this server.
     */
    protected void _startClientListener(){               
        if(this.__new_connection_listener!=null){
            if(this.__new_connection_listener.isAlive()){
                return;  //Don't want to accidentally make a new thread!
            }
        }
        
        this.__new_connection_listener=new Thread(new Runnable(){
            @Override public void run(){
                try{
                    while(true){
                        System.out.print("Listening for incoming connection.\n");
                        ConnectionServer.this._acceptClient(new ConnectionClient(ConnectionServer.this.__socket.accept()));
                    }
                }catch(IOException e){
                    //TODO_HIGH:  Make a handler for this exception here
                }
            }
        });
        this.__new_connection_listener.run();
    }    
    /*End Other Essential Methods*/
}
