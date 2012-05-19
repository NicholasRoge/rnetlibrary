package roge.net;

import java.io.Serializable;

/**
 * Used to ensure no signals are confused when received.  //TODO:  Come up with a better way to phrase this.
 * 
 * @author Nicholas Rogé
 */
public abstract class Signal implements Serializable{
    private static final long serialVersionUID = -1940431652860706155L;
    
    private String  __message;
    private Integer __message_code;
    
    
    /*Begin Constructors*/
    /**
     * Default constructor for the object;  Sets both the message, and message code to null.
     */
    public Signal(){
        this(null,null);
    }
    
    /**
     * Constructs the object with the given message.
     * 
     * @param message Message that can be retrieved with the <code>getMessage()</code> method.
     * 
     * @see {@link Signal#getMessage()}
     */
    public Signal(String message){
        this(message,null);
    }
    
    /**
     * Constructs the object with the given message code.
     * 
     * @param message_code Numeric value that can be used to uniquely identify which message was sent.  Can be retrieved using the <code>getMessageCode()</code> method.
     * 
     * @see {@link Signal#getMessageCode()}
     */
    public Signal(Integer message_code){
        this(null,message_code);
    }
    
    /**
     * Constructs the object with the given message and message code.
     * 
     * @param message Message that can be retrieved with the <code>getMessage()</code> method.
     * @param message_code Numeric value that can be used to uniquely identify which message was sent.  Can be retrieved using the <code>getMessageCode()</code> method.
     * 
     * @see {@link Signal#getMessage()}
     * @see {@link Signal#getMessageCode()}
     */
    public Signal(String message,Integer message_code){
        this.__message=message;
        this.__message_code=message_code;
    }
    /*End Constructors*/
    
    /*Begin Getter Methods*/
    /**
     * Returns the message that can further describe the given signal.
     * 
     * @return The message that can further describe the given signal.
     */
    public String getMessage(){
        return this.__message;
    }
    
    /**
     * Returns the numeric value that can be used to uniquely identify which message was sent.
     * 
     * @return The numeric value that can be used to uniquely identify which message was sent.
     */
    public Integer getMessageCode(){
        return this.__message_code;
    }
    /*End Getter Methods*/
}
