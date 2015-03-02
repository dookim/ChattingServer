
/**
 * Created by dookim on 2/8/15.
 */
public class User {
    public String email;
    public String provider;
    public String univ;
    public String chatId;
    public User(String email, String univ, String provider, String chatId) {
        this.email = email;
        this.univ = univ;
        this.provider = provider;
        this.chatId = chatId;
    }
}
