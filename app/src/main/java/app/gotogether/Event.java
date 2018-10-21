package app.gotogether;

import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.firestore.GeoPoint;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

class Event extends EventForDB implements Serializable {

    private String id;
    private ArrayList<User> participantsList = new ArrayList<User>();

    /** empty constructor
     * required to module a nnew object directly when fetching from database  */
    public Event(){}

    public Event(String title, String destination, int participants, String img){
        setTitle(title);
        super.setstreet(destination);
        super.setParticipants(participants);
        if (img != null)
            super.setImage(img);
        else
            super.setImage("ic_launcher_round");
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
    public String getTitle() {
        return super.getTitle();
    }

    public String getstreet() {
        return super.getstreet();
    }

    public Object getLatLng() {
        return super.getLatLng();
    }

    public int getParticipants() {
        return super.getParticipants();
    }

    public String getImage() {
        return super.getImage();
    }

    public ArrayList<User> getParticipantsList() { return participantsList; }

    public void addParticipant(User u){ participantsList.add(u); }

    public void setParticipantsList(ArrayList<User> participantsList) {
        this.participantsList = participantsList;
    }

    public boolean isCompleted() {
        return super.isCompleted();
    }

    public Map<String, Object> getDestination() {
        return super.getDestination();
    }

    public ArrayList<Object> getDrivers() {
        return super.getDrivers();
    }

    public ArrayList<Map<Object, Object>> getCluster() {
        return super.getCluster();
    }

    @Override
    public String toString() {
        return "my title: "+getTitle()+"; my destination: "+getstreet()+"; my participants: "+getParticipants();
    }

    public void setTitle(String title) {
        super.setTitle(title);
    }

    public void setCompleted(boolean completed) {
        super.setCompleted(completed);
    }

    public void setDestination(Map<String, Object> destination) {
        super.setDestination(destination);
    }

    public void setstreet(String addr) {
        super.setstreet(addr);
    }

    public void setLatLng(GeoPoint gp) {
        super.setLatLng(gp);
    }

    public void setDrivers(ArrayList<Object> drivers) {
        super.setDrivers(drivers);
    }

    public void setCluster(ArrayList<Map<Object, Object>> cluster) {
        super.setCluster(cluster);
    }

    public void setParticipants(int participants) {
        super.setParticipants(participants);
    }

    public void setImage(String image) {
        super.setImage(image);
    }
}
