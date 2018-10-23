package app.gotogether;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;

public class User implements Parcelable, Serializable {

    private String id;
    private String username;
    private String startAddress;
    private LatLng startLatLng = null;
    private boolean isDriver = false;
    private int seats = -1;

    // empty constructor
    public User() {
    }

    // constructor with seats means seats != 0
    public User(String id, String username, String startAddress, LatLng startLatLng, int seats){
        this.username=username;
        this.startAddress=startAddress;
        this.startLatLng=startLatLng;
        this.isDriver=true;
        this.seats=seats;
    }

    // constructor with no seats means not a volunteering driver & no available seats
    public User(String id, String username, String startAddress, LatLng startLatLng){
        this.username=username;
        this.startAddress=startAddress;
        this.startLatLng=startLatLng;
    }

    // constructor to save driving user with no defined pickup point
    public User(String id, String username, String startAddress, int seats){
        this.username=username;
        this.startAddress=startAddress;
        if ( (this.seats = seats) != -1)
            this.isDriver = true;
    }

    // constructor to save non-driving user with no defined pickup point
    public User(String id, String username, String startAddress){
        this.username=username;
        this.startAddress=startAddress;
    }

    protected User(Parcel in) {
        username = in.readString();
        startAddress = in.readString();
        startLatLng = in.readParcelable(LatLng.class.getClassLoader());
        isDriver = in.readByte() != 0;
        seats = in.readInt();
    }

    public static final Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) {
            return new User(in);
        }

        @Override
        public User[] newArray(int size) {
            return new User[size];
        }
    };

    /**
     * Describe the kinds of special objects contained in this Parcelable
     * instance's marshaled representation. For example, if the object will
     * include a file descriptor in the output of {@link #writeToParcel(Parcel, int)},
     * the return value of this method must include the
     * {@link #CONTENTS_FILE_DESCRIPTOR} bit.
     *
     * @return a bitmask indicating the set of special object types marshaled
     * by this Parcelable object instance.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Flatten this object in to a Parcel.
     *
     * @param dest  The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     *              May be 0 or {@link #PARCELABLE_WRITE_RETURN_VALUE}.
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(username);
        dest.writeString(startAddress);
        dest.writeParcelable(startLatLng, flags);
        dest.writeByte((byte) (isDriver ? 1 : 0));
        dest.writeInt(seats);
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setStartAddress(String startAddress) {
        this.startAddress = startAddress;
    }

    public void setStartLatLng(LatLng startLatLng) {
        this.startLatLng = startLatLng;
    }

    public void setDriver(boolean driver) {
        isDriver = driver;
    }

    public void setSeats(int seats) {
        this.seats = seats;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getStartAddress() {
        return startAddress;
    }

    public LatLng getStartLatLng() {
        return startLatLng;
    }

    public boolean isDriver() {
        return isDriver;
    }

    public int getSeats() {
        return seats;
    }


}
