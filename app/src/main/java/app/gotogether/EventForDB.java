package app.gotogether;

import com.google.firebase.firestore.GeoPoint;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class EventForDB implements Serializable {

    private String title;
    private boolean  completed;
    private Map<String, Object> destination = new HashMap<>();
    private ArrayList<Object> drivers;

    private ArrayList<Map<Object, Object>> cluster;
    private int participants;
    private String image = "ic_launcher_round";

    /** empty constructor
     * required to module a new object directly when fetching from database  */
    public EventForDB(){}

    public EventForDB(String title, String destination, int participants, String img){
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

    /*public String getstreet() {
        return (String) destination.get("street");
    }

    public GeoPoint getLatLng() {
        return (GeoPoint) destination.get("LatLng");
    }*/

    public int getParticipants() {
        return participants;
    }

    public String getImage() {
        return image;
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

    public void setTitle(String title) {
        this.title = title;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void setDestination(Map<String, Object> destination) {
        this.destination = destination;
    }

    public void setstreet(String addr) {
        this.destination.put("street", addr);
    }

    public void setLatLng(GeoPoint gp) {
        this.destination.put("LatLng", gp);
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
