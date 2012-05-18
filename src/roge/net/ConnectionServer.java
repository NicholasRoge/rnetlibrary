package roge.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import roge.net.ConnectionClient.DataReceivedListener;

/**
 * @author Nicholas Rogé
 */
public class ConnectionServer{       
    public static final String CONNECT_SUCCESS="connect_success";
    public static final String CONNECT_FAILURE="connect_failure";
    
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
    
    private List<ConnectionClient>      __clients;
    private List<ClientConnectListener> __client_connect_listeners;
    private int                         __port;
    private ServerSocket                __socket;
    private Thread                      __new_connection_listener;
    
    
    /*Begin Constructors*/
    public ConnectionServer(int port){
        this.__port=port;
    }
    /*End Constructors*/
    
    /*Begin Getter Methods*/
    public List<ConnectionClient> getClientList(){
        if(this.__clients==null){
            this.__clients=new ArrayList<ConnectionClient>();
        }
        
        return this.__clients;
    }
    
    public List<ClientConnectListener> getClientConnectListeners(){
        if(this.__client_connect_listeners==null){
            this.__client_connect_listeners=new ArrayList<ClientConnectListener>();
        }
        
        return this.__client_connect_listeners;
    }
    /*End Getter Methods*/
    
    /*Begin Other Essential Methods*/
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

            System.out.print("Client connected from "+client.getIP()+" at "+formatter.format(new Date())+"\n\n");
        }else{
            try{
                client.send(ConnectionServer.CONNECT_FAILURE);
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }
    
    public void addClientConnectListener(ClientConnectListener listener){
        if(!this.getClientConnectListeners().contains(listener)){
            this.getClientConnectListeners().add(listener);
        }
    }
    
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
    
    protected void _startClientListener(){               
        this.__new_connection_listener=new Thread(){
            @Override public void run(){
                try{
                    while(true){
                        System.out.print("Listening for incoming connection.\n");
                        ConnectionServer.this._acceptClient(new ConnectionClient(ConnectionServer.this.__socket.accept(),false));
                    }
                }catch(IOException e){
                    //TODO_HIGH:  Make a handler for this exception here
                }
            }
        };
        this.__new_connection_listener.start();
    }    
    /*End Other Essential Methods*/
}
