
public class Abortable {  
    public volatile boolean done = false;  
      
    public Abortable() {  
        init();  
    }  
      
    public void init() {  
        done = false;  
    }  
      
    public synchronized boolean isDone() {  
        return done;  
    }  
}  
