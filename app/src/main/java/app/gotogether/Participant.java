package app.gotogether;

public class Participant {

    public String username;
    public boolean isUser;

    public Participant ( String username) { this.username = username; this.isUser = false;}

    public Participant ( String username, boolean isUser) { this.username = username; this.isUser =  isUser;}
}
