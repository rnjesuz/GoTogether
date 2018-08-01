package app.gotogether;

import java.io.Serializable;
import java.util.ArrayList;

class Event implements Serializable {

    private String title;
    private String destination;
    private int participants;
    private String image;

    private ArrayList<User> participantsList = new ArrayList<User>();


    public Event(String title, String destination, int participants, String img){
        this.title = title;
        this.destination = destination;
        this.participants = participants;
        this .image = img;
    }

    public String getTitle() {
        return title;
    }

    public String getDestination() {
        return destination;
    }

    public int getParticipants() {
        return participants;
    }

    public String getImage() {
        return image;
    }

    public ArrayList<User> getParticipantsList() { return participantsList; }

    public void addParticipant(User u){ participantsList.add(u); }

    public void setParticipantsList(ArrayList<User> participantsList) {
        this.participantsList = participantsList;
    }
}
