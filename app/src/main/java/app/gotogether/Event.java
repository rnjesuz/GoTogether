package app.gotogether;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class Event implements Serializable {

    private String title;
    private String id;
    private boolean  completed;
    private Map<String, Object> destination = new HashMap<>();
    private ArrayList<Object> drivers;

    private ArrayList<Map<Object, Object>> cluster;
    private int participants;
    private String image;
    private ArrayList<User> participantsList = new ArrayList<User>();

    /** empty constructor
     * required to module a nnew object directly when fetching from database  */
    public Event(){}

    public Event(String title, String destination, int participants, String img){
        this.title = title;
        this.destination.put("street", destination);
        this.participants = participants;
        if (img != null)
            this.image = img;
        else
            this.image = "ic_launcher_round";
    }

    public String getTitle() {
        return title;
    }

    public String getDestinationStreet() {
        return (String) destination.get("street");
    }

    public Object getDestinationLatLng() {
        return destination.get("LatLng");
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

    public String getId() {
        return id;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Map<String, Object> getDestination() {
        return destination;
    }

    public ArrayList<Object> getDrivers() {
        return drivers;
    }

    public ArrayList<Map<Object, Object>> getCluster() {
        return cluster;
    }

    @Override
    public String toString() {
        return "my title: "+getTitle()+"; my destination: "+getDestinationStreet()+"; my participants: "+getParticipants();
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setDestination(Map<String, Object> destination) {
        this.destination = destination;
    }

    public void setDestinationStreet(String addr) {
        this.destination.put("street", addr);
    }

    public void setDestinationLatLng(LatLng latlng) {
        this.destination.put("LatLng", latlng);
    }

    public void setDrivers(ArrayList<Object> drivers) {
        this.drivers = drivers;
    }

    public void setCluster(ArrayList<Map<Object, Object>> cluster) {
        this.cluster = cluster;
    }

    public void setParticipants(int participants) {
        this.participants = participants;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
