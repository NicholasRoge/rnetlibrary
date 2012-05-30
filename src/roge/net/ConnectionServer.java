package roge.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import roge.net.ConnectionClient.DataReceivedListener;
import roge.net.ConnectionClient.SignalReceivedListener;

/**
 * @author Nicholas Rogé
 */
public class ConnectionServer implements DataReceivedListener,SignalReceivedListener{
    /**List of signals this object may send.*/
    public static class Signals{
        /**Signal that should be sent when a client is closing its connection to the server.*/
        public static class CloseConnection extends Signal{
            private static final long serialVersionUID = 2882637983514840538L;
        };
    }
    /**Data that will be received by the client if its connection to the server was a success.*/
    public static class ConnectSuccessSignal extends Signal{
        private static final long serialVersionUID = -1261872129312562381L;
    };
    /**Data that will be received by the client if its connection to the server was a failure.*/
    public static class ConnectFailureSignal extends Signal{
        private static final long serialVersionUID = -5165976077218168691L;
    };
    
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
    private boolean                        __verbose;
    
    
    /*Begin Constructors*/
    /**
     * Constructs the object, telling it to listen to the given port.
     * 
     * @param port Port this object should listen to.
     */
    public ConnectionServer(int port){
        this.__port=port;
        
        this.setVerbose(false);
    }
    /*End Constructors*/
    
    /*Begin Overridden Methods*/
    @Override public void onDataReceived(ConnectionClient client,Object data){
    }
    
    @Override public void onSignalReceived(ConnectionClient client,Signal signal){        
        if(signal instanceof Signals.CloseConnection){
            for(ClientDisconnectListener listener:this.getClientDisconnectListeners()){
                listener.onClientDisconnect(client);
            }
            
            client.disconnect(false);
            
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
    
    /**
     * Gets the status of the server, that is to say, whether it has been started or not.  
     * 
     * @Return Returns <code>true</code> if the server has been started and is currently listening for incoming data, and <code>false</code> otherwise.
     */
    public boolean isListening(){
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
                client.send(new ConnectSuccessSignal(),false);
            }catch(IOException e){
                e.toString();
            }

            client.addDataRecievedListener(this);
            client.addSignalReceivedListener(this);
            if(this.__verbose){
                System.out.print("Client connected from "+client.getIP()+" at "+formatter.format(new Date())+"\n\n");
            }
        }else{
            try{
                client.send(new ConnectFailureSignal(),false);
            }catch(IOException e){
                if(this.__verbose){
                    e.printStackTrace();
                }
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
                        if(ConnectionServer.this.__verbose){
                            System.out.print("Listening for incoming connection.\n");
                        }
                        ConnectionServer.this._acceptClient(new ConnectionClient(ConnectionServer.this.__socket.accept()));
                    }
                }catch(IOException e){
                    //TODO_HIGH:  Make a handler for this exception here
                }
            }
        });
        this.__new_connection_listener.start();
    }
    
    /**
     * Stops the server.
     */
    public void stop(){
        try{
            for(ConnectionClient client:this.getClientList()){
                client.send(new Signals.CloseConnection(),true);
                
                this.onSignalReceived(client,new Signals.CloseConnection());
            }
            
            this.__new_connection_listener.interrupt();
            this.__socket.close();
        }catch(IOException e){
            System.out.println("An error occured while attemptting to stop the server.  Message:  "+e.getMessage());
            if(this.__verbose){
                e.printStackTrace();
            }
            
            return;
        }
        
        System.out.println("The server stopped successfully.");
    }
    /*End Other Essential Methods*/
}
